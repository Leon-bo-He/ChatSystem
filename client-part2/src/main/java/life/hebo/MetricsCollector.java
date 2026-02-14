package life.hebo;

import lombok.Getter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Getter
public class MetricsCollector {

    private final ConcurrentLinkedQueue<MetricsRecord> records = new ConcurrentLinkedQueue<>();

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger reconnectionCount = new AtomicInteger(0);
    private final AtomicLong totalLatency = new AtomicLong(0);

    public void recordSuccess(MetricsRecord record) {
        records.add(record);
        successCount.incrementAndGet();
        totalLatency.addAndGet(record.getLatencyMs());
    }

    public void recordFailure(MetricsRecord record) {
        records.add(record);
        failedCount.incrementAndGet();
    }

    public void incrementConnections() {
        totalConnections.incrementAndGet();
    }

    public void incrementReconnections() {
        reconnectionCount.incrementAndGet();
    }

    public void printStatistics(long wallTimeMs) {
        List<MetricsRecord> all = new ArrayList<>(records);
        List<Double> latenciesMs = all.stream()
                .filter(r -> r.getStatusCode() == 200 && r.getLatencyMs() >= 0)
                .mapToDouble(r -> r.getLatencyMs() / 1000.0)
                .sorted()
                .boxed()
                .collect(Collectors.toList());


        System.out.println("\n************* ChatSystem Client Performance Metrics *************");

        System.out.println("\n── Summary ──────────────────────────────────────────");
        System.out.printf("  Successful messages : %,d%n", successCount.get());
        System.out.printf("  Failed messages     : %,d%n", failedCount.get());
        System.out.printf("  Total runtime (wall time)  : %,.2f s%n", wallTimeMs / 1000.0);
        System.out.printf("  Overall throughput  : %,.2f msg/s%n", successCount.get() / (wallTimeMs / 1000.0));

        System.out.println("\n── Connection Statistics ────────────────────────────");
        System.out.printf("  Total connections   : %,d%n", totalConnections.get());
        System.out.printf("  Reconnections       : %,d%n", reconnectionCount.get());

        if (latenciesMs.isEmpty()) {
            System.out.println("\n  No successful latency samples recorded.");
            return;
        }

        // Latency (sub-ms precision via nanoTime → micros → ms)
        double mean   = latenciesMs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double median = percentile(latenciesMs, 50);
        double p95    = percentile(latenciesMs, 95);
        double p99    = percentile(latenciesMs, 99);
        double min    = latenciesMs.get(0);
        double max    = latenciesMs.get(latenciesMs.size() - 1);

        System.out.println("\n── Latency (ms) ────────────────────────────────────");
        System.out.printf("  Mean    : %,.2f%n", mean);
        System.out.printf("  Median  : %,.2f%n", median);
        System.out.printf("  P95     : %,.2f%n", p95);
        System.out.printf("  P99     : %,.2f%n", p99);
        System.out.printf("  Min     : %,.2f%n", min);
        System.out.printf("  Max     : %,.2f%n", max);

        // Throughput per room
        Map<Integer, Long> perRoom = all.stream()
                .filter(r -> r.getStatusCode() == 200)
                .collect(Collectors.groupingBy(MetricsRecord::getRoomId, Collectors.counting()));

        System.out.println("\n── Throughput per Room ──────────────────────────────");
        perRoom.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf("  Room %2d : %,6d messages  (%,.1f msg/s)%n",
                        e.getKey(), e.getValue(),
                        e.getValue() / (wallTimeMs / 1000.0)));

        // Message-type distribution
        Map<String, Long> typeDist = all.stream()
                .collect(Collectors.groupingBy(r -> r.getMessageType().name(), Collectors.counting()));


        System.out.println("\n── Message Type Distribution ────────────────────────");
        typeDist.forEach((type, count) ->
                System.out.printf("  %-6s : %,d (%.1f%%)%n",
                        type, count, 100.0 * count / all.size()));

        System.out.println("\n════════════════════════════════════════════════════");
    }

    // CSV Export
    public void writeCsv(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());

            try (BufferedWriter bw = Files.newBufferedWriter(path)) {
                bw.write("timestamp,messageType,latency_micros,statusCode,roomId");
                bw.newLine();
                for (MetricsRecord r : records) {
                    bw.write(r.toCsvRow());
                    bw.newLine();
                }
            }
            System.out.println("CSV written to: " + path.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
        }
    }

    public Map<Long, Integer> getThroughputBuckets(int bucketSeconds) {
        TreeMap<Long, Integer> buckets = new TreeMap<>();
        int successRecords = 0;

        for (MetricsRecord r : records) {
            if (r.getStatusCode() != 200) continue;
            successRecords++;

            // Convert timestamp to seconds, then round down to bucket boundary
            long timestampSec = r.getSendTimestamp() / 1000;
            long bucketKey = (timestampSec / bucketSeconds) * bucketSeconds;
            buckets.merge(bucketKey, 1, Integer::sum);
        }

        if (successRecords > 0 && buckets.isEmpty()) {
            System.err.println("WARNING: Have success records but no buckets created!");
        }

        return buckets;
    }

    private double percentile(List<Double> sorted, int p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }

}
