package life.hebo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class ConsumerProperties {

    private final Consumer consumer = new Consumer();
    private final Persistence persistence = new Persistence();

    public Consumer getConsumer() {
        return consumer;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public static class Consumer {
        private int workerThreads = 4;
        private int prefetchCount = 200;
        private int consumerTimeoutSeconds = 30;
        private String dlqExchange = "chat.deadletter.exchange";
        private String dlqQueue = "chat.broadcast.dlq";
        private String dlqRoutingKey = "chat.broadcast.dlq";

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getPrefetchCount() {
            return prefetchCount;
        }

        public void setPrefetchCount(int prefetchCount) {
            this.prefetchCount = prefetchCount;
        }

        public int getConsumerTimeoutSeconds() {
            return consumerTimeoutSeconds;
        }

        public void setConsumerTimeoutSeconds(int consumerTimeoutSeconds) {
            this.consumerTimeoutSeconds = consumerTimeoutSeconds;
        }

        public String getDlqExchange() {
            return dlqExchange;
        }

        public void setDlqExchange(String dlqExchange) {
            this.dlqExchange = dlqExchange;
        }

        public String getDlqQueue() {
            return dlqQueue;
        }

        public void setDlqQueue(String dlqQueue) {
            this.dlqQueue = dlqQueue;
        }

        public String getDlqRoutingKey() {
            return dlqRoutingKey;
        }

        public void setDlqRoutingKey(String dlqRoutingKey) {
            this.dlqRoutingKey = dlqRoutingKey;
        }
    }

    public static class Persistence {
        private int batchSize = 1000;
        private int flushIntervalMs = 500;
        private int writerThreads = 2;
        private int writeQueueCapacity = 20000;
        private int maxRetries = 5;
        private long baseBackoffMs = 100;
        private long maxBackoffMs = 5000;
        private int circuitBreakerThreshold = 10;
        private long circuitBreakerOpenMs = 30000;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getFlushIntervalMs() {
            return flushIntervalMs;
        }

        public void setFlushIntervalMs(int flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }

        public int getWriterThreads() {
            return writerThreads;
        }

        public void setWriterThreads(int writerThreads) {
            this.writerThreads = writerThreads;
        }

        public int getWriteQueueCapacity() {
            return writeQueueCapacity;
        }

        public void setWriteQueueCapacity(int writeQueueCapacity) {
            this.writeQueueCapacity = writeQueueCapacity;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getBaseBackoffMs() {
            return baseBackoffMs;
        }

        public void setBaseBackoffMs(long baseBackoffMs) {
            this.baseBackoffMs = baseBackoffMs;
        }

        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }

        public void setMaxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
        }

        public int getCircuitBreakerThreshold() {
            return circuitBreakerThreshold;
        }

        public void setCircuitBreakerThreshold(int circuitBreakerThreshold) {
            this.circuitBreakerThreshold = circuitBreakerThreshold;
        }

        public long getCircuitBreakerOpenMs() {
            return circuitBreakerOpenMs;
        }

        public void setCircuitBreakerOpenMs(long circuitBreakerOpenMs) {
            this.circuitBreakerOpenMs = circuitBreakerOpenMs;
        }
    }
}
