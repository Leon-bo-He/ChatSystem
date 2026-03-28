package life.hebo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

public class RoomManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserInfo> activeUsers = new ConcurrentHashMap<>();
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong deliveryFailures = new AtomicLong(0);

    public void addSessionToRoom(String roomId, WebSocketSession session, String userId, String username) {
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
        activeUsers.put(session.getId(), new UserInfo(session.getId(), userId, username, roomId, session));
    }

    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        UserInfo info = activeUsers.remove(sessionId);
        if (info != null && info.getRoomId() != null) {
            Set<WebSocketSession> sessions = roomSessions.get(info.getRoomId());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(info.getRoomId());
                }
            }
        }
    }

    public Set<WebSocketSession> getSessionsForRoom(String roomId) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    public UserInfo getUserInfo(String sessionId) {
        return activeUsers.get(sessionId);
    }

    public boolean broadcastToRoom(String roomId, BroadcastPayload payload) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return true;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            deliveryFailures.incrementAndGet();
            return false;
        }

        TextMessage message = new TextMessage(json);
        boolean anySent = false;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                removeSession(session);
                continue;
            }
            try {
                session.sendMessage(message);
                anySent = true;
            } catch (IOException e) {
                deliveryFailures.incrementAndGet();
                removeSession(session);
            }
        }
        if (anySent) {
            messagesProcessed.incrementAndGet();
        }
        return anySent;
    }

    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }

    public long getDeliveryFailures() {
        return deliveryFailures.get();
    }

    public int getActiveRoomCount() {
        return roomSessions.size();
    }

    public int getActiveUserCount() {
        return activeUsers.size();
    }

    public ConcurrentHashMap<String, Set<WebSocketSession>> getRoomSessions() {
        return roomSessions;
    }

    public ConcurrentHashMap<String, UserInfo> getActiveUsers() {
        return activeUsers;
    }
}
