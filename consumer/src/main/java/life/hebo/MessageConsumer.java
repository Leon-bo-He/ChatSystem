package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageConsumer implements Runnable {

    private static final String EXCHANGE_NAME = "chat.exchange";
    private static final String CONSUMER_QUEUE_NAME = "chat.broadcast";
    private static final String ROUTING_KEY_PREFIX = "room.";
    private static final int PREFETCH_COUNT = 10;
    private static final int MAX_RETRIES = 3;
    private static final long DEDUPE_TTL_MS = 60000;
    private static final int DEDUPE_MAX_SIZE = 50000;

    private final ConnectionFactory connectionFactory;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final int consumerIndex;
    private final AtomicBoolean running = new AtomicBoolean(true);

    /** Recent message IDs for duplicate detection (bounded, best-effort) */
    private final ConcurrentHashMap<String, Long> recentMessageIds = new ConcurrentHashMap<>();

    public MessageConsumer(ConnectionFactory connectionFactory, RoomManager roomManager,
                           ObjectMapper objectMapper, int consumerIndex) {
        this.connectionFactory = connectionFactory;
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
        this.consumerIndex = consumerIndex;
    }

    @Override
    public void run() {
        Connection connection = null;
        Channel channel = null;
        try {
            connection = connectionFactory.newConnection("consumer-" + consumerIndex);
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
            channel.queueDeclare(CONSUMER_QUEUE_NAME, true, false, false, null);
            channel.queueBind(CONSUMER_QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY_PREFIX + "#");
            channel.basicQos(PREFETCH_COUNT);

            Channel finalChannel = channel;
            String consumerTag = channel.basicConsume(CONSUMER_QUEUE_NAME, false,
                    (tag, delivery) -> handleDelivery(finalChannel, delivery), ct -> {});

            while (running.get()) {
                Thread.sleep(500);
            }
            if (channel.isOpen()) {
                channel.basicCancel(consumerTag);
            }
        } catch (IOException | TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Consumer " + consumerIndex + " failed", e);
        } finally {
            closeQuietly(channel);
            closeQuietly(connection);
        }
    }

    public void stop() {
        running.set(false);
    }

    private void handleDelivery(Channel channel, Delivery delivery) {
        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        int retries = 0;
        while (retries <= MAX_RETRIES) {
            try {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                QueueMessage qm = objectMapper.readValue(body, QueueMessage.class);
                if (isDuplicate(qm.getMessageId())) {
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
                boolean success = roomManager.broadcastToRoom(qm.getRoomId(), payload);
                if (success) {
                    markProcessed(qm.getMessageId());
                    ack(channel, deliveryTag);
                    return;
                }
                // Broadcast failed (e.g. serialization or all sends failed); retry with backoff
                retries++;
                if (retries > MAX_RETRIES) {
                    nackRequeue(channel, deliveryTag);
                    return;
                }
                Thread.sleep(100L * retries);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                nackRequeue(channel, deliveryTag);
                return;
            } catch (Exception e) {
                retries++;
                if (retries > MAX_RETRIES) {
                    nackRequeue(channel, deliveryTag);
                    return;
                }
                try {
                    Thread.sleep(100L * retries);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    nackRequeue(channel, deliveryTag);
                    return;
                }
            }
        }
        nackRequeue(channel, deliveryTag);
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
}
