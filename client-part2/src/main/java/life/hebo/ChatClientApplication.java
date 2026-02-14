package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.*;

public class ChatClientApplication {
    public static void main(String[] args) throws Exception {
        org.slf4j.LoggerFactory.getLogger(ChatClientApplication.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        MetricsCollector metrics = new MetricsCollector();
        ConnectionManager connManager = new ConnectionManager(metrics, 15);

        BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(ClientConfig.QUEUE_CAPACITY);

        // message generator
        MessageGenerator generator = new MessageGenerator(queue, ClientConfig.TOTAL_MESSAGES);
        Thread generatorThread = new Thread(generator, "msg-generator");
        generatorThread.start();

        long overallStart = System.currentTimeMillis();

        // warmup
        System.out.println("\nStarting Warmup Phase ...");
        long warmupStart = System.currentTimeMillis();

        CountDownLatch warmupLatch = new CountDownLatch(ClientConfig.WARMUP_THREADS);
        ExecutorService warmupPool = Executors.newFixedThreadPool(
                ClientConfig.WARMUP_THREADS, new DaemonThreadFactory("warmup"));

        for (int i = 0; i < ClientConfig.WARMUP_THREADS; i++) {
            warmupPool.submit(new MessageSender(
                    queue, connManager, metrics, mapper,
                    ClientConfig.WARMUP_MESSAGES_PER_THREAD, warmupLatch));
        }

        warmupLatch.await();
        warmupPool.shutdown();
        long warmupMs = System.currentTimeMillis() - warmupStart;

        System.out.printf("  Warmup complete: %,d msgs in %.2f s  (%.0f msg/s)%n",
                ClientConfig.WARMUP_TOTAL_MESSAGES, warmupMs / 1000.0,
                ClientConfig.WARMUP_TOTAL_MESSAGES / (warmupMs / 1000.0));

        // main phase
        System.out.println("\nStarting Main Phase ...");
        long mainStart = System.currentTimeMillis();

        int remaining = ClientConfig.TOTAL_MESSAGES - ClientConfig.WARMUP_TOTAL_MESSAGES;
        int perThread = remaining / ClientConfig.MAIN_PHASE_THREADS;
        int extras    = remaining % ClientConfig.MAIN_PHASE_THREADS;

        CountDownLatch mainLatch = new CountDownLatch(ClientConfig.MAIN_PHASE_THREADS);
        ExecutorService mainPool = Executors.newFixedThreadPool(
                ClientConfig.MAIN_PHASE_THREADS, new DaemonThreadFactory("sender"));

        for (int i = 0; i < ClientConfig.MAIN_PHASE_THREADS; i++) {
            int count = perThread + (i < extras ? 1 : 0);
            mainPool.submit(new MessageSender(
                    queue, connManager, metrics, mapper, count, mainLatch));
        }

        // Progress monitor
        Thread monitor = new Thread(() -> {
            try {
                while (mainLatch.getCount() > 0) {
                    Thread.sleep(5000);
                    int done = metrics.getSuccessCount().get() + metrics.getFailedCount().get();
                    double pct = 100.0 * done / ClientConfig.TOTAL_MESSAGES;
                    System.out.printf("  Progress: %,d / %,d (%.1f%%)  failed=%,d%n",
                            done, ClientConfig.TOTAL_MESSAGES, pct, metrics.getFailedCount().get());
                }
            } catch (InterruptedException ignored) {}
        }, "progress-monitor");
        monitor.setDaemon(true);
        monitor.start();

        mainLatch.await();
        mainPool.shutdown();

        long mainMs     = System.currentTimeMillis() - mainStart;
        long totalMs    = System.currentTimeMillis() - overallStart;

        System.out.printf("\n  Main phase complete: %.2f s%n", mainMs / 1000.0);

        // Wait for generator to finish
        generatorThread.join(5000);

        // Results
        double actualThroughput = metrics.getSuccessCount().get() / (totalMs / 1000.0);
        metrics.printStatistics(totalMs);

        // CSV export
        metrics.writeCsv(ClientConfig.CSV_FILE_PATH);

        // Throughput chart
        Map<Long, Integer> buckets = metrics.getThroughputBuckets(ClientConfig.BUCKET_SECONDS);
        ThroughputChart.generate(buckets, ClientConfig.BUCKET_SECONDS, ClientConfig.CHART_FILE_PATH);

        // Cleanup
        connManager.closeAll();

        System.out.println("\nTest complete.");

    }

    @RequiredArgsConstructor
    private static class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private int counter = 0;

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + (counter++));
            t.setDaemon(true);
            return t;
        }
    }
}
