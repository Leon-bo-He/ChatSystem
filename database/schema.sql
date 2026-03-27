DROP TABLE IF EXISTS messages CASCADE;
DROP MATERIALIZED VIEW IF EXISTS user_stats CASCADE;
DROP MATERIALIZED VIEW IF EXISTS room_stats CASCADE;
DROP MATERIALIZED VIEW IF EXISTS hourly_stats CASCADE;

-- ============================================================
-- Main Messages Table
-- ============================================================
CREATE TABLE messages (
    message_id VARCHAR(64) PRIMARY KEY,
    room_id INT NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,

    -- Message timestamp (from client)
    message_ts TIMESTAMPTZ NOT NULL,

    -- Database insert timestamp
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Indexes for Core Queries
-- ============================================================

-- Index 1: For Query "Get messages for room in time range"
CREATE INDEX idx_room_messages_in_time_range
    ON messages (room_id, message_ts DESC)
    INCLUDE (message_id, user_id, content);

-- Index 2: For Query "Get user's message history"
CREATE INDEX idx_user_message_history
    ON messages (user_id, message_ts DESC)
    INCLUDE (room_id, content);

-- Index 3: For Query "Count active users in time window"
CREATE INDEX idx_users_active_in_time_range
    ON messages USING BRIN (message_ts)
    WITH (pages_per_range = 128);

-- Index 4: For Query "Get rooms user has participated in"
CREATE INDEX idx_user_rooms_participated
    ON messages (user_id, room_id, message_ts DESC);

-- ============================================================
-- Materialized Views for Analytics
-- ============================================================

-- Materialized View 1: User Statistics
CREATE MATERIALIZED VIEW user_stats AS
SELECT
    user_id,
    COUNT(*) AS message_count,
    MIN(message_ts) AS first_message,
    MAX(message_ts) AS last_message,
    COUNT(DISTINCT room_id) AS rooms_participated,
    NOW() AS last_refreshed
FROM messages
GROUP BY user_id;

CREATE UNIQUE INDEX idx_user_stats_user_id ON user_stats (user_id);
CREATE INDEX idx_user_stats_msg_count ON user_stats (message_count DESC);

-- Materialized View 2: Room Statistics
CREATE MATERIALIZED VIEW room_stats AS
SELECT
    room_id,
    COUNT(*) AS message_count,
    COUNT(DISTINCT user_id) AS unique_users,
    MIN(message_ts) AS first_message,
    MAX(message_ts) AS last_activity,
    NOW() AS last_refreshed
FROM messages
GROUP BY room_id;

CREATE UNIQUE INDEX idx_room_stats_room_id ON room_stats (room_id);
CREATE INDEX idx_room_stats_msg_count ON room_stats (message_count DESC);
CREATE INDEX idx_room_stats_activity ON room_stats (last_activity DESC);

-- Materialized View 3: Hourly Statistics
CREATE MATERIALIZED VIEW hourly_stats AS
SELECT
    DATE_TRUNC('hour', message_ts) AS hour,
    COUNT(*) AS message_count,
    COUNT(DISTINCT user_id) AS unique_users,
    COUNT(DISTINCT room_id) AS active_rooms,
    NOW() AS last_refreshed
FROM messages
GROUP BY DATE_TRUNC('hour', message_ts)
ORDER BY hour;

CREATE UNIQUE INDEX idx_hourly_stats_hour ON hourly_stats (hour DESC);

-- ============================================================
-- Helper Functions
-- ============================================================

-- Function to refresh all materialized views
CREATE OR REPLACE FUNCTION refresh_all_stats()
    RETURNS TABLE (
    view_name TEXT,
    refresh_time INTERVAL
) AS $$
DECLARE
    start_time TIMESTAMPTZ;
    end_time TIMESTAMPTZ;
BEGIN
    -- Refresh user_stats
    start_time := clock_timestamp();
    REFRESH MATERIALIZED VIEW CONCURRENTLY user_stats;
    end_time := clock_timestamp();
    view_name := 'user_stats';
    refresh_time := end_time - start_time;
    RETURN NEXT;

    -- Refresh room_stats
    start_time := clock_timestamp();
    REFRESH MATERIALIZED VIEW CONCURRENTLY room_stats;
    end_time := clock_timestamp();
    view_name := 'room_stats';
    refresh_time := end_time - start_time;
    RETURN NEXT;

    -- Refresh hourly_stats
    start_time := clock_timestamp();
    REFRESH MATERIALIZED VIEW CONCURRENTLY hourly_stats;
    end_time := clock_timestamp();
    view_name := 'hourly_stats';
    refresh_time := end_time - start_time;
    RETURN NEXT;
END;
$$ LANGUAGE plpgsql;