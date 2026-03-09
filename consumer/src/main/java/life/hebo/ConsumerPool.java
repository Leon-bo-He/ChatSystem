package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsumerPool implements Runnable {

    private static final String EXCHANGE_NAME = "chat.exchange";
    private static final String CONSUMER_QUEUE_NAME = "chat.broadcast";
    private static final String ROUTING_KEY_PREFIX = "room.";
    private static final int PREFETCH_COUNT = 50;
    private static final int MAX_RETRIES = 3;
    private static final long DEDUPE_TTL_MS = 60000;
    private static final int DEDUPE_MAX_SIZE = 50000;

    private final ConnectionFactory connectionFactory;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final int workerCount;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final ConcurrentHashMap<String, Long> recentMessageIds = new ConcurrentHashMap<>();
    private final ExecutorService[] workers;

    public ConsumerPool(ConnectionFactory connectionFactory, RoomManager roomManager,
                        ObjectMapper objectMapper, int workerCount) {
        this.connectionFactory = connectionFactory;
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
        this.workerCount = Math.max(1, workerCount);
        this.workers = new ExecutorService[this.workerCount];
        for (int i = 0; i < this.workerCount; i++) {
            final int workerIndex = i;
            workers[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "consumer-worker-" + workerIndex);
                t.setDaemon(false);
                return t;
            });
        }
    }

    @Override
    public void run() {
        Connection connection = null;
        Channel channel = null;
        try {
            connection = connectionFactory.newConnection("consumer-pool");
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
            channel.queueDeclare(CONSUMER_QUEUE_NAME, true, false, false, null);
            channel.queueBind(CONSUMER_QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY_PREFIX + "#");
            channel.basicQos(PREFETCH_COUNT);

            final Channel consumerChannel = channel;
            channel.basicConsume(CONSUMER_QUEUE_NAME, false, (consumerTag, delivery) -> {
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                byte[] body = delivery.getBody();
                String roomId;
                try {
                    QueueMessage qm = objectMapper.readValue(new String(body, StandardCharsets.UTF_8), QueueMessage.class);
                    roomId = qm.getRoomId() != null ? qm.getRoomId() : "default";
                } catch (Exception e) {
                    ack(consumerChannel, deliveryTag);
                    return;
                }
                int workerIndex = Math.abs(roomId.hashCode() % workerCount);
                Future<?> future = workers[workerIndex].submit(() ->
                        processDelivery(consumerChannel, deliveryTag, body));
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    nackRequeue(consumerChannel, deliveryTag);
                    return;
                } catch (TimeoutException e) {
                    future.cancel(true);
                    nackRequeue(consumerChannel, deliveryTag);
                    return;
                } catch (ExecutionException e) {
                    nackRequeue(consumerChannel, deliveryTag);
                    return;
                }
                ack(consumerChannel, deliveryTag);
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
            for (ExecutorService w : workers) {
                w.shutdown();
                try {
                    w.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            }
            closeQuietly(channel);
            closeQuietly(connection);
        }
    }

    /**
     * Process one delivery; retries on failure. Returns on success/duplicate (caller acks); throws on final failure (caller nack+requeue).
     */
    private void processDelivery(Channel channel, long deliveryTag, byte[] body) {
        int retries = 0;
        while (retries <= MAX_RETRIES) {
            try {
                String json = new String(body, StandardCharsets.UTF_8);
                QueueMessage qm = objectMapper.readValue(json, QueueMessage.class);
                if (isDuplicate(qm.getMessageId())) {
                    return; // duplicate: caller will ack
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
                boolean success = roomManager.broadcastToRoom(qm.getRoomId(), payload);
                if (success) {
                    markProcessed(qm.getMessageId());
                    return;
                }
                // Broadcast failed; retry with backoff
                retries++;
                if (retries > MAX_RETRIES) {
                    throw new RuntimeException("Broadcast failed after " + MAX_RETRIES + " retries");
                }
                Thread.sleep(100L * retries);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            } catch (Exception e) {
                retries++;
                if (retries > MAX_RETRIES) {
                    throw new RuntimeException("Failed after " + MAX_RETRIES + " retries", e);
                }
                try {
                    Thread.sleep(100L * retries);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }

    private boolean isDuplicate(String messageId) {
        if (messageId == null) return false;
        if (recentMessageIds.containsKey(messageId)) return true;
        evictOldEntries();
        if (recentMessageIds.size() >= DEDUPE_MAX_SIZE) return false;
        recentMessageIds.put(messageId, System.currentTimeMillis());
        return false;
    }

    private void markProcessed(String messageId) {
        if (messageId != null) {
            recentMessageIds.put(messageId, System.currentTimeMillis());
        }
        evictOldEntries();
    }

    private void evictOldEntries() {
        long now = System.currentTimeMillis();
        recentMessageIds.entrySet().removeIf(e -> now - e.getValue() > DEDUPE_TTL_MS);
        while (recentMessageIds.size() > DEDUPE_MAX_SIZE) {
            Optional<String> first = recentMessageIds.keySet().stream().findFirst();
            first.ifPresent(recentMessageIds::remove);
        }
    }

    private void ack(Channel channel, long deliveryTag) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException ignored) {}
        }
    }

    private void nackRequeue(Channel channel, long deliveryTag) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ignored) {}
        }
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
}
