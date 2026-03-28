package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

public class ConsumerWebSocketHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConsumerWebSocketHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = getRoomIdFromSession(session);
        String userId = getQueryParam(session, "userId", session.getId());
        String username = getQueryParam(session, "username", "user-" + session.getId().substring(0, 8));
        roomManager.addSessionToRoom(roomId, session, userId, username);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // allow client to send JOIN with userId/username to update identity
        try {
            Map<?, ?> payload = objectMapper.readValue(message.getPayload(), Map.class);
            Object u = payload.get("userId");
            Object n = payload.get("username");
            if (u != null || n != null) {
                UserInfo info = roomManager.getUserInfo(session.getId());
                if (info != null) {
                    String userId = u != null ? u.toString() : info.getUserId();
                    String username = n != null ? n.toString() : info.getUsername();
                    roomManager.removeSession(session);
                    roomManager.addSessionToRoom(info.getRoomId(), session, userId, username);
                }
            }
        } catch (Exception ignored) {
            // ignore malformed; client may just be sending other data
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        roomManager.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        roomManager.removeSession(session);
    }

    private String getRoomIdFromSession(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        // Expect /chat/{roomId}
        if (path.startsWith("/chat/")) {
            String suffix = path.substring(6);
            int slash = suffix.indexOf('/');
            return slash >= 0 ? suffix.substring(0, slash) : suffix;
        }
        return "default";
    }

    private static String getQueryParam(WebSocketSession session, String name, String defaultValue) {
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return defaultValue;
        }
        String query = session.getUri().getQuery();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return defaultValue;
    }
}
