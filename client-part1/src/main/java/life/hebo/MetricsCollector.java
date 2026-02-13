package life.hebo;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Getter
public class MetricsCollector {

    private final ConcurrentLinkedQueue<MetricsRecord> records = new ConcurrentLinkedQueue<>();

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger reconnectionCount = new AtomicInteger(0);

    public void recordSuccess(MetricsRecord record) {
        records.add(record);
        successCount.incrementAndGet();
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
        List<Long> latencies = all.stream()
                .filter(r -> r.getStatusCode() == 200)
                .map(MetricsRecord::getLatencyMs)
                .sorted()
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
    }

}
