package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageValidator validator = new MessageValidator();
    // Map to store active WebSocket sessions, keyed by session ID
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

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

            // Create server response
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
        System.out.println("WebSocket connection closed: " + session.getId() + " with status: " + status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.err.println("Transport error for session " + session.getId() + ": " + exception.getMessage());
        sessions.remove(session.getId());
    }

    private void echoBackToSender(WebSocketSession session, ServerResponse response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(responseJson));
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

}
