package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ConsumerPool implements Runnable {

    private static final String EXCHANGE_NAME = "chat.exchange";
    private static final String CONSUMER_QUEUE_NAME = "chat.broadcast";
    private static final String ROUTING_KEY_PREFIX = "room.";
    private static final long DEDUPE_TTL_MS = 300000;
    private static final int DEDUPE_MAX_SIZE = 200000;

    private final ConnectionFactory connectionFactory;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final MessagePersistenceService persistenceService;
    private final ConsumerStatsAggregator statsAggregator;
    private final ConsumerProperties properties;
    private final int workerCount;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final ConcurrentHashMap<String, Long> recentMessageIds = new ConcurrentHashMap<>();
    private final ExecutorService consumerWorkers;
    private final ExecutorService[] dbWriters;
    private final BlockingQueue<PersistTask> writeQueue;
    private final Object channelAckLock = new Object();
    private final AtomicInteger consecutiveDbFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntilMs = new AtomicLong(0L);

    public ConsumerPool(ConnectionFactory connectionFactory, RoomManager roomManager,
                        ObjectMapper objectMapper, MessagePersistenceService persistenceService,
                        ConsumerStatsAggregator statsAggregator, ConsumerProperties properties) {
        this.connectionFactory = connectionFactory;
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
        this.persistenceService = persistenceService;
        this.statsAggregator = statsAggregator;
        this.properties = properties;

        this.workerCount = Math.max(1, properties.getConsumer().getWorkerThreads());
        this.consumerWorkers = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, "consumer-worker");
            t.setDaemon(false);
            return t;
        });

        int writerThreads = Math.max(1, properties.getPersistence().getWriterThreads());
        this.dbWriters = new ExecutorService[writerThreads];
        for (int i = 0; i < writerThreads; i++) {
            final int writerIndex = i;
            dbWriters[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "db-writer-" + writerIndex);
                t.setDaemon(false);
                return t;
            });
        }

        int queueCapacity = Math.max(1000, properties.getPersistence().getWriteQueueCapacity());
        this.writeQueue = new ArrayBlockingQueue<>(queueCapacity);
    }

    @Override
    public void run() {
        Connection connection = null;
        Channel channel = null;
        try {
            statsAggregator.start();
            connection = connectionFactory.newConnection("consumer-pool");
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
            declareQueueWithDlq(channel);
            channel.queueBind(CONSUMER_QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY_PREFIX + "#");
            channel.basicQos(Math.max(1, properties.getConsumer().getPrefetchCount()));

            for (ExecutorService writer : dbWriters) {
                writer.submit(this::dbWriterLoop);
            }

            final Channel consumerChannel = channel;
            channel.basicConsume(CONSUMER_QUEUE_NAME, false, (consumerTag, delivery) -> {
                try {
                    consumerWorkers.submit(() -> processDelivery(consumerChannel, delivery));
                } catch (RejectedExecutionException ex) {
                    nackRequeue(consumerChannel, delivery.getEnvelope().getDeliveryTag(), true);
                }
            }, ct -> {});

            while (running.get()) {
                Thread.sleep(1000);
            }
        } catch (IOException | TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Consumer pool failed", e);
        } finally {
            for (ExecutorService w : dbWriters) {
                w.shutdown();
                try {
                    w.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            }
            consumerWorkers.shutdown();
            try {
                consumerWorkers.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            statsAggregator.stop();
            closeQuietly(channel);
            closeQuietly(connection);
        }
    }

    private void processDelivery(Channel channel, Delivery delivery) {
        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        byte[] body = delivery.getBody();
        try {
            QueueMessage qm = objectMapper.readValue(new String(body, StandardCharsets.UTF_8), QueueMessage.class);
            if (isDuplicate(qm.getMessageId())) {
                statsAggregator.incrementDuplicates(1);
                ack(channel, deliveryTag);
                return;
            }
            BroadcastPayload payload = new BroadcastPayload(
                    qm.getMessageId(),
                    qm.getRoomId(),
                    qm.getUserId(),
                    qm.getUsername(),
                    qm.getMessage(),
                    qm.getTimestamp(),
                    qm.getMessageType()
            );
            roomManager.broadcastToRoom(qm.getRoomId(), payload);

            CompletableFuture<WriteResult> future = new CompletableFuture<>();
            PersistTask task = new PersistTask(qm, future);
            if (!writeQueue.offer(task, 2, TimeUnit.SECONDS)) {
                nackRequeue(channel, deliveryTag, true);
                return;
            }
            statsAggregator.incrementQueued();

            WriteResult result = future.get(properties.getConsumer().getConsumerTimeoutSeconds(), TimeUnit.SECONDS);
            if (result == WriteResult.SUCCESS || result == WriteResult.DUPLICATE) {
                markProcessed(qm.getMessageId());
                ack(channel, deliveryTag);
            } else if (result == WriteResult.DLQ) {
                statsAggregator.incrementDeadLettered();
                nackRequeue(channel, deliveryTag, false);
            } else {
                nackRequeue(channel, deliveryTag, true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            nackRequeue(channel, deliveryTag, true);
        } catch (TimeoutException e) {
            nackRequeue(channel, deliveryTag, true);
        } catch (Exception e) {
            ack(channel, deliveryTag);
        }
    }

    private void dbWriterLoop() {
        List<PersistTask> batch = new ArrayList<>(properties.getPersistence().getBatchSize());
        long flushIntervalMs = Math.max(50, properties.getPersistence().getFlushIntervalMs());

        while (running.get()) {
            try {
                PersistTask first = writeQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                }
                writeQueue.drainTo(batch, properties.getPersistence().getBatchSize() - batch.size());
                if (batch.isEmpty()) {
                    continue;
                }
                flushBatch(batch);
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                failBatch(batch, WriteResult.RETRY);
                batch.clear();
            }
        }
    }

    private void flushBatch(List<PersistTask> batch) {
        if (isCircuitOpen()) {
            failBatch(batch, WriteResult.RETRY);
            return;
        }

        int maxRetries = Math.max(1, properties.getPersistence().getMaxRetries());
        long baseBackoff = Math.max(10L, properties.getPersistence().getBaseBackoffMs());
        long maxBackoff = Math.max(baseBackoff, properties.getPersistence().getMaxBackoffMs());

        int retries = 0;
        while (retries <= maxRetries) {
            try {
                List<QueueMessage> payload = batch.stream().map(t -> t.message).toList();
                int[] results = persistenceService.upsertBatch(payload, properties.getPersistence().getBatchSize());
                long changedRows = 0;
                for (int value : results) {
                    if (value > 0) {
                        changedRows++;
                    }
                }
                long duplicates = Math.max(0, batch.size() - changedRows);
                statsAggregator.incrementPersisted(changedRows);
                statsAggregator.incrementDuplicates(duplicates);
                consecutiveDbFailures.set(0);
                completeBatch(batch, WriteResult.SUCCESS);
                return;
            } catch (Exception ex) {
                retries++;
                statsAggregator.incrementDbFailures();
                int failures = consecutiveDbFailures.incrementAndGet();
                if (failures >= properties.getPersistence().getCircuitBreakerThreshold()) {
                    circuitOpenUntilMs.set(System.currentTimeMillis() + properties.getPersistence().getCircuitBreakerOpenMs());
                    statsAggregator.incrementCircuitOpen();
                }
                if (retries > maxRetries) {
                    failBatch(batch, WriteResult.DLQ);
                    return;
                }
                long backoff = Math.min(maxBackoff, baseBackoff * (1L << (retries - 1)));
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failBatch(batch, WriteResult.RETRY);
                    return;
                }
            }
        }
    }

    private void completeBatch(List<PersistTask> batch, WriteResult result) {
        for (PersistTask task : batch) {
            task.future.complete(result);
        }
    }

    private void failBatch(List<PersistTask> batch, WriteResult result) {
        for (PersistTask task : batch) {
            task.future.complete(result);
        }
    }

    private boolean isCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntilMs.get();
    }

    private boolean isDuplicate(String messageId) {
        if (messageId == null) return false;
        evictOldEntries();
        return recentMessageIds.containsKey(messageId);
    }

    private void markProcessed(String messageId) {
        if (messageId == null) return;
        if (recentMessageIds.size() >= DEDUPE_MAX_SIZE) {
            evictOldEntries();
        }
        if (recentMessageIds.size() < DEDUPE_MAX_SIZE) {
            recentMessageIds.put(messageId, System.currentTimeMillis());
        }
    }

    private void evictOldEntries() {
        long now = System.currentTimeMillis();
        recentMessageIds.entrySet().removeIf(e -> now - e.getValue() > DEDUPE_TTL_MS);
    }

    private void ack(Channel channel, long deliveryTag) {
        synchronized (channelAckLock) {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.basicAck(deliveryTag, false);
                } catch (IOException ignored) {}
            }
        }
    }

    private void nackRequeue(Channel channel, long deliveryTag, boolean requeue) {
        synchronized (channelAckLock) {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.basicNack(deliveryTag, false, requeue);
                } catch (IOException ignored) {}
            }
        }
    }

    private void declareQueueWithDlq(Channel channel) throws IOException {
        String dlqExchange = properties.getConsumer().getDlqExchange();
        String dlqQueue = properties.getConsumer().getDlqQueue();
        String dlqRoutingKey = properties.getConsumer().getDlqRoutingKey();

        channel.exchangeDeclare(dlqExchange, "direct", true);
        channel.queueDeclare(dlqQueue, true, false, false, null);
        channel.queueBind(dlqQueue, dlqExchange, dlqRoutingKey);

        Map<String, Object> args = Map.of(
                "x-dead-letter-exchange", dlqExchange,
                "x-dead-letter-routing-key", dlqRoutingKey
        );
        channel.queueDeclare(CONSUMER_QUEUE_NAME, true, false, false, args);
    }

    private static void closeQuietly(Channel c) {
        if (c != null) try { c.close(); } catch (Exception ignored) {}
    }

    private static void closeQuietly(Connection c) {
        if (c != null) try { c.close(); } catch (Exception ignored) {}
    }

    public void stop() {
        running.set(false);
    }

    private static final class PersistTask {
        private final QueueMessage message;
        private final CompletableFuture<WriteResult> future;

        private PersistTask(QueueMessage message, CompletableFuture<WriteResult> future) {
            this.message = message;
            this.future = future;
        }
    }

    private enum WriteResult {
        SUCCESS,
        DUPLICATE,
        RETRY,
        DLQ
    }
}
