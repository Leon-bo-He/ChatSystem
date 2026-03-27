package life.hebo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricsService {
    private final JdbcTemplate jdbcTemplate;

    public MetricsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMetrics(
            Integer roomId,
            Instant roomStartTime,
            Instant roomEndTime,
            String userId,
            Instant userStartTime,
            Instant userEndTime,
            Instant activeStartTime,
            Instant activeEndTime,
            String bucket,
            int topN,
            boolean refreshViews
    ) {
        String normalizedBucket = "second".equalsIgnoreCase(bucket) ? "second" : "minute";

        long startedAt = System.currentTimeMillis();
        Map<String, Object> core = new HashMap<>();
        Map<String, Object> analytics = new HashMap<>();
        Map<String, Object> queryLatencyMs = new HashMap<>();
        Map<String, Object> cache = new HashMap<>();

        if (refreshViews) {
            long refreshStart = System.currentTimeMillis();
            cache.put("refreshResult", jdbcTemplate.queryForList("SELECT * FROM refresh_all_stats()"));
            cache.put("refreshLatencyMs", System.currentTimeMillis() - refreshStart);
        }

        long qStart = System.currentTimeMillis();
        core.put("messagesForRoom", getMessagesForRoom(roomId, roomStartTime, roomEndTime));
        queryLatencyMs.put("messagesForRoom", System.currentTimeMillis() - qStart);

        qStart = System.currentTimeMillis();
        core.put("userMessageHistory", getUserMessageHistory(userId, userStartTime, userEndTime));
        queryLatencyMs.put("userMessageHistory", System.currentTimeMillis() - qStart);

        qStart = System.currentTimeMillis();
        core.put("activeUsers", getActiveUsers(activeStartTime, activeEndTime));
        queryLatencyMs.put("activeUsers", System.currentTimeMillis() - qStart);

        qStart = System.currentTimeMillis();
        core.put("roomsParticipated", getRoomsUserParticipated(userId));
        queryLatencyMs.put("roomsParticipated", System.currentTimeMillis() - qStart);

        qStart = System.currentTimeMillis();
        analytics.put("messagesPerBucket", getMessagesPerBucket(normalizedBucket));
        queryLatencyMs.put("messagesPerBucket", System.currentTimeMillis() - qStart);

        qStart = System.currentTimeMillis();
        analytics.put("mostActiveUsers", getMostActiveUsersFromView(topN));
        queryLatencyMs.put("mostActiveUsers", System.currentTimeMillis() - qStart);

        qStart = System.currentTimeMillis();
        analytics.put("mostActiveRooms", getMostActiveRoomsFromView(topN));
        queryLatencyMs.put("mostActiveRooms", System.currentTimeMillis() - qStart);

        qStart = System.currentTimeMillis();
        analytics.put("userParticipationPatterns", getUserParticipationPatternsFromView());
        queryLatencyMs.put("userParticipationPatterns", System.currentTimeMillis() - qStart);

        Map<String, Object> response = new HashMap<>();
        response.put("generatedAt", Instant.now().toString());
        response.put("coreQueries", core);
        response.put("analyticsQueries", analytics);
        response.put("cache", cache);
        response.put("queryLatencyMs", queryLatencyMs);
        response.put("totalLatencyMs", System.currentTimeMillis() - startedAt);
        return response;
    }

    private List<Map<String, Object>> getMessagesForRoom(Integer roomId, Instant startTime, Instant endTime) {
        return jdbcTemplate.queryForList(
                """
                        SELECT message_id, room_id, user_id, content, message_ts, created_at
                        FROM messages
                        WHERE room_id = ?
                          AND message_ts >= ?
                          AND message_ts <= ?
                        ORDER BY message_ts ASC
                        """,
                roomId,
                Timestamp.from(startTime),
                Timestamp.from(endTime)
        );
    }

    private List<Map<String, Object>> getUserMessageHistory(String userId, Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return jdbcTemplate.queryForList(
                    """
                            SELECT message_id, room_id, user_id, content, message_ts, created_at
                            FROM messages
                            WHERE user_id = ?
                            ORDER BY message_ts DESC
                            """,
                    userId
            );
        }

        return jdbcTemplate.queryForList(
                """
                        SELECT message_id, room_id, user_id, content, message_ts, created_at
                        FROM messages
                        WHERE user_id = ?
                          AND message_ts >= ?
                          AND message_ts <= ?
                        ORDER BY message_ts DESC
                        """,
                userId,
                Timestamp.from(startTime),
                Timestamp.from(endTime)
        );
    }

    private Map<String, Object> getActiveUsers(Instant startTime, Instant endTime) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(DISTINCT user_id)
                        FROM messages
                        WHERE message_ts >= ?
                          AND message_ts <= ?
                        """,
                Integer.class,
                Timestamp.from(startTime),
                Timestamp.from(endTime)
        );
        Map<String, Object> result = new HashMap<>();
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        result.put("uniqueUserCount", count == null ? 0 : count);
        return result;
    }

    private List<Map<String, Object>> getRoomsUserParticipated(String userId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT room_id, MAX(message_ts) AS last_activity
                        FROM messages
                        WHERE user_id = ?
                        GROUP BY room_id
                        ORDER BY last_activity DESC
                        """,
                userId
        );
    }

    private List<Map<String, Object>> getMessagesPerBucket(String bucket) {
        String trunc = "second".equals(bucket) ? "second" : "minute";
        return jdbcTemplate.queryForList(
                """
                        SELECT DATE_TRUNC(?, message_ts) AS bucket_start,
                               COUNT(*) AS message_count
                        FROM messages
                        GROUP BY bucket_start
                        ORDER BY bucket_start ASC
                        """,
                trunc
        );
    }

    private List<Map<String, Object>> getMostActiveUsersFromView(int topN) {
        return jdbcTemplate.queryForList(
                """
                        SELECT user_id, message_count, rooms_participated, first_message, last_message
                        FROM user_stats
                        ORDER BY message_count DESC
                        LIMIT ?
                        """,
                topN
        );
    }

    private List<Map<String, Object>> getMostActiveRoomsFromView(int topN) {
        return jdbcTemplate.queryForList(
                """
                        SELECT room_id, message_count, unique_users, first_message, last_activity
                        FROM room_stats
                        ORDER BY message_count DESC
                        LIMIT ?
                        """,
                topN
        );
    }

    private Map<String, Object> getUserParticipationPatternsFromView() {
        List<Map<String, Object>> hourlySeries = jdbcTemplate.queryForList(
                """
                        SELECT hour, message_count, unique_users, active_rooms
                        FROM hourly_stats
                        ORDER BY hour ASC
                        """
        );

        List<Map<String, Object>> byHourOfDay = jdbcTemplate.queryForList(
                """
                        SELECT EXTRACT(HOUR FROM hour AT TIME ZONE 'UTC')::INT AS hour_utc,
                               SUM(message_count)::BIGINT AS message_count,
                               SUM(unique_users)::BIGINT AS approx_unique_users
                        FROM hourly_stats
                        GROUP BY hour_utc
                        ORDER BY hour_utc
                        """
        );

        // Day-of-week still comes from raw events because hourly_stats is grouped by hour.
        List<Map<String, Object>> byDayOfWeek = jdbcTemplate.queryForList(
                """
                        SELECT EXTRACT(DOW FROM message_ts AT TIME ZONE 'UTC')::INT AS day_of_week_utc,
                               COUNT(*) AS message_count,
                               COUNT(DISTINCT user_id) AS unique_users
                        FROM messages
                        GROUP BY day_of_week_utc
                        ORDER BY day_of_week_utc
                        """
        );

        Map<String, Object> patterns = new HashMap<>();
        patterns.put("hourlySeries", hourlySeries);
        patterns.put("byHourUtc", byHourOfDay);
        patterns.put("byDayOfWeekUtc", byDayOfWeek);
        return patterns;
    }
}
