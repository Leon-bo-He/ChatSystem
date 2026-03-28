package life.hebo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class MessagePersistenceService {

    private static final String UPSERT_SQL = """
            INSERT INTO messages (message_id, room_id, user_id, content, message_ts)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (message_id) DO UPDATE
            SET room_id = EXCLUDED.room_id,
                user_id = EXCLUDED.user_id,
                content = EXCLUDED.content,
                message_ts = EXCLUDED.message_ts
            """;

    private final JdbcTemplate jdbcTemplate;

    public MessagePersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int[] upsertBatch(List<QueueMessage> messages, int batchSize) {
        ParameterizedPreparedStatementSetter<QueueMessage> pss = this::setStatement;
        return jdbcTemplate.batchUpdate(UPSERT_SQL, messages, batchSize, pss);
    }

    private void setStatement(PreparedStatement ps, QueueMessage qm) throws SQLException {
        ps.setString(1, qm.getMessageId());
        ps.setInt(2, parseRoomId(qm.getRoomId()));
        ps.setString(3, qm.getUserId());
        ps.setString(4, qm.getMessage());
        ps.setTimestamp(5, Timestamp.from(parseTimestamp(qm.getTimestamp())));
    }

    private int parseRoomId(String roomId) {
        try {
            return Integer.parseInt(roomId);
        } catch (Exception e) {
            return -1;
        }
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }
}
