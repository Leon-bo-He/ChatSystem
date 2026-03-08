package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageValidator validator = new MessageValidator();
    private final MessageQueuePublisher messageQueuePublisher;

    private final String serverId = Optional.ofNullable(System.getenv("SERVER_ID"))
            .orElseGet(() -> UUID.randomUUID().toString());

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** Per-session state: must JOIN before TEXT/LEAVE; after LEAVE, no more TEXT. */
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    private static final class SessionState {
        boolean hasJoined;
        boolean hasLeft;
    }

    public ChatWebSocketHandler(MessageQueuePublisher messageQueuePublisher) {
        this.messageQueuePublisher = messageQueuePublisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        System.out.println("WebSocket connection established: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            // Parse incoming message
            ChatMessage chatMessage = objectMapper.readValue(message.getPayload(), ChatMessage.class);

            // Enforce JOIN → TEXT/LEAVE ordering: must JOIN first; after LEAVE, no more TEXT
            String stateError = checkMessageTypeVsSessionState(session.getId(), chatMessage.getMessageType());
            if (stateError != null) {
                ServerResponse response = new ServerResponse(
                        "ERROR",
                        Instant.now().toString(),
                        stateError
                );
                echoBackToSender(session, response);
                return;
            }

            // Validate message
            MessageValidator.ValidationResult validation = validator.validate(chatMessage);

            if (!validation.isValid()) {
                ServerResponse response = new ServerResponse(
                        "ERROR",
                        Instant.now().toString(),
                        validation.getErrorMessage()
                );
                echoBackToSender(session, response);
                return;
            }

            // Publish to message queue
            String clientIp = extractClientIp(session);
            boolean published = messageQueuePublisher.publish(chatMessage, serverId, clientIp);

            if (!published) {
                ServerResponse response = new ServerResponse(
                        "ERROR",
                        Instant.now().toString(),
                        "Failed to enqueue message. Please try again later."
                );
                echoBackToSender(session, response);
                return;
            }

            // Update session state only after successful enqueue
            updateSessionState(session.getId(), chatMessage.getMessageType());

            // Create server response acknowledging successful enqueue
            ServerResponse response = new ServerResponse(
                    "SUCCESS",
                    Instant.now().toString(),
                    "Message received from user " + chatMessage.getUsername()
            );
            echoBackToSender(session, response);

        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            ServerResponse response = new ServerResponse(
                    "ERROR",
                    Instant.now().toString(),
                    "Invalid message format: " + e.getMessage()
            );
            echoBackToSender(session, response);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        sessionStates.remove(session.getId());
        System.out.println("WebSocket connection closed: " + session.getId() + " with status: " + status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.err.println("Transport error for session " + session.getId() + ": " + exception.getMessage());
        sessions.remove(session.getId());
        sessionStates.remove(session.getId());
    }

    private String extractClientIp(WebSocketSession session) {
        InetSocketAddress remoteAddress = session.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private void echoBackToSender(WebSocketSession session, ServerResponse response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(responseJson));
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    /**
     * Check message type against session state: must JOIN before TEXT/LEAVE; after LEAVE, no more TEXT.
     * @return error message if invalid, null if allowed
     */
    private String checkMessageTypeVsSessionState(String sessionId, MessageType messageType) {
        if (messageType == null) return null; // validator will catch this
        SessionState state = sessionStates.get(sessionId);
        boolean hasJoined = state != null && state.hasJoined;
        boolean hasLeft = state != null && state.hasLeft;

        switch (messageType) {
            case JOIN:
                if (hasLeft) return "Cannot JOIN again after LEAVE";
                return null;
            case TEXT:
                if (!hasJoined) return "Must JOIN before sending messages";
                if (hasLeft) return "Cannot send TEXT after LEAVE";
                return null;
            case LEAVE:
                if (!hasJoined) return "Must JOIN before sending LEAVE";
                if (hasLeft) return "Already left";
                return null;
            default:
                return null;
        }
    }

    private void updateSessionState(String sessionId, MessageType messageType) {
        if (messageType == null) return;
        SessionState state = sessionStates.computeIfAbsent(sessionId, k -> new SessionState());
        switch (messageType) {
            case JOIN:
                state.hasJoined = true;
                break;
            case LEAVE:
                state.hasLeft = true;
                break;
            default:
                break;
        }
    }

}
