package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageQueuePublisher {

    private static final String EXCHANGE_NAME = "chat.exchange";
    private static final String EXCHANGE_TYPE = "topic";

    private static final long MESSAGE_TTL_MILLIS = 600000L;
    private static final int QUEUE_MAX_LENGTH = 10000;

    private static final int FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_RESET_MILLIS = 30000L;

    private final ChannelPool channelPool;
    private final ObjectMapper objectMapper;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenedAt = 0L;

    public MessageQueuePublisher(ChannelPool channelPool, ObjectMapper objectMapper) {
        this.channelPool = channelPool;
        this.objectMapper = objectMapper;
    }

    public boolean publish(ChatMessage chatMessage, String serverId, String clientIp) {
        if (isCircuitOpen()) {
            System.err.println("RabbitMQ circuit breaker is open; skipping publish.");
            return false;
        }

        Channel channel = null;
        try {
            channel = channelPool.borrowChannel();
            if (channel == null) {
                throw new IllegalStateException("No RabbitMQ channel available from pool");
            }

            channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, true);

            String roomId = chatMessage.getRoomId();
            String routingKey = "room." + roomId;

            Map<String, Object> args = new HashMap<>();
            args.put("x-message-ttl", MESSAGE_TTL_MILLIS);
            args.put("x-max-length", QUEUE_MAX_LENGTH);

            String queueName = "room." + roomId;
            channel.queueDeclare(queueName, true, false, false, args);
            channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

            QueueMessage queueMessage = new QueueMessage(
                    UUID.randomUUID().toString(),
                    roomId,
                    chatMessage.getUserId(),
                    chatMessage.getUsername(),
                    chatMessage.getMessage(),
                    chatMessage.getTimestamp(),
                    chatMessage.getMessageType(),
                    serverId,
                    clientIp
            );

            byte[] body = objectMapper.writeValueAsBytes(queueMessage);

            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .timestamp(Date.from(Instant.now()))
                    .build();

            channel.basicPublish(EXCHANGE_NAME, routingKey, properties, body);

            consecutiveFailures.set(0);
            return true;
        } catch (Exception ex) {
            System.err.println("Failed to publish message to RabbitMQ: " + ex.getMessage());
            handleFailure();
            return false;
        } finally {
            if (channel != null) {
                channelPool.returnChannel(channel);
            }
        }
    }

    private boolean isCircuitOpen() {
        if (!circuitOpen) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - circuitOpenedAt >= CIRCUIT_RESET_MILLIS) {
            circuitOpen = false;
            consecutiveFailures.set(0);
            return false;
        }
        return true;
    }

    private void handleFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (!circuitOpen && failures >= FAILURE_THRESHOLD) {
            circuitOpen = true;
            circuitOpenedAt = System.currentTimeMillis();
            System.err.println("Opening RabbitMQ circuit breaker after " + failures + " consecutive failures.");
        }
    }
}

