package life.hebo;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam Integer roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant roomStartTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant roomEndTime,
            @RequestParam String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant userStartTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant userEndTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant activeStartTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant activeEndTime,
            @RequestParam(defaultValue = "minute") String bucket,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "false") boolean refreshViews
    ) {
        Instant now = Instant.now();
        Instant resolvedActiveStart = activeStartTime == null ? now.minus(1, ChronoUnit.HOURS) : activeStartTime;
        Instant resolvedActiveEnd = activeEndTime == null ? now : activeEndTime;

        Map<String, Object> payload = metricsService.getMetrics(
                roomId,
                roomStartTime,
                roomEndTime,
                userId,
                userStartTime,
                userEndTime,
                resolvedActiveStart,
                resolvedActiveEnd,
                bucket,
                topN,
                refreshViews
        );

        return ResponseEntity.ok(payload);
    }
}
