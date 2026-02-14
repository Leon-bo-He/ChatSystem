package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
public class MessageSender implements Runnable {

    private final BlockingQueue<ChatMessage> queue;
    private final ConnectionManager connectionManager;
    private final MetricsCollector metrics;
    private final ObjectMapper mapper;
    private final int messageCount;
    private final CountDownLatch latch;

    @Override
    public void run() {
        try {
            for (int i = 0; i < messageCount; i++) {
                ChatMessage msg = queue.poll(10, TimeUnit.SECONDS);
                sendWithRetry(msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            latch.countDown();
        }
    }

    private void sendWithRetry(ChatMessage msg) {
        long backoff = ClientConfig.INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= ClientConfig.MAX_RETRIES; attempt++) {
            ConnectionManager.PooledConnection conn = null;
            try {
                conn = connectionManager.borrowConnection(msg.getRoomId());

                String json = mapper.writeValueAsString(msg);

                CompletableFuture<String> responseFuture = conn.prepareForResponse();
                long sendTs = System.currentTimeMillis();
                long startNs = System.nanoTime();
                conn.send(json);

                // Wait for echo
                responseFuture.get(1, TimeUnit.SECONDS);
                long latencyMicros = (System.nanoTime() - startNs) / 1_000;

                metrics.recordSuccess(new MetricsRecord(
                        sendTs, msg.getMessageType(), latencyMicros, 200, msg.getRoomId()));
                return;

            } catch (Exception e) {
                // Reconnect broken connections
                if (conn != null && !conn.isOpen()) {
                    try {
                        connectionManager.reconnect(conn);
                        metrics.incrementReconnections();
                    } catch (Exception ignored) {}
                }

                // Track failed messages after 5 retries
                if (attempt == ClientConfig.MAX_RETRIES) {
                    metrics.recordFailure(new MetricsRecord(
                            System.currentTimeMillis(), msg.getMessageType(),
                            -1, 0, msg.getRoomId()));
                } else {
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    backoff *= 2;
                }
            } finally {
                if (conn != null) connectionManager.returnConnection(conn);
            }
        }
    }
}