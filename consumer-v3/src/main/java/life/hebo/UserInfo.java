package life.hebo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {

    private String sessionId;
    private String userId;
    private String username;
    private String roomId;
    private transient WebSocketSession session;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserInfo userInfo = (UserInfo) o;
        return Objects.equals(sessionId, userInfo.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}
