package life.hebo;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ConsumerStatsAggregator {

    private final AtomicLong queuedForPersistence = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong dbFailures = new AtomicLong();
    private final AtomicLong deadLettered = new AtomicLong();
    private final AtomicLong circuitOpenCount = new AtomicLong();

    private ScheduledExecutorService scheduler;

    public void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "consumer-stats-aggregator");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::logSnapshot, 30, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public void incrementQueued() {
        queuedForPersistence.incrementAndGet();
    }

    public void incrementPersisted(long count) {
        persisted.addAndGet(count);
    }

    public void incrementDuplicates(long count) {
        duplicates.addAndGet(count);
    }

    public void incrementDbFailures() {
        dbFailures.incrementAndGet();
    }

    public void incrementDeadLettered() {
        deadLettered.incrementAndGet();
    }

    public void incrementCircuitOpen() {
        circuitOpenCount.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new HashMap<>();
        map.put("queuedForPersistence", queuedForPersistence.get());
        map.put("persisted", persisted.get());
        map.put("duplicates", duplicates.get());
        map.put("dbFailures", dbFailures.get());
        map.put("deadLettered", deadLettered.get());
        map.put("circuitOpenCount", circuitOpenCount.get());
        return map;
    }

    private void logSnapshot() {
        Map<String, Object> snapshot = snapshot();
        System.out.println("consumer_stats=" + snapshot);
    }
}
