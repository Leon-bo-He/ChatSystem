package life.hebo;

import lombok.RequiredArgsConstructor;

import java.util.Random;
import java.util.concurrent.BlockingQueue;

@RequiredArgsConstructor
public class MessageGenerator implements Runnable {

    private final BlockingQueue<ChatMessage> messageQueue;
    private final int totalMessages;
    private final Random random = new Random();

    @Override
    public void run() {
        int messageCount = 0;
        while (messageCount < totalMessages) {
            try {
                ChatMessage message = generateMessage();
                messageQueue.put(message);
                messageCount++;

                // Log progress every 10,000 messages
                if (messageCount % 10000 == 0) {
                    System.out.println("Generated " + messageCount + " messages");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Message generation interrupted: " + e.getMessage());
                break;
            }
        }
    }

    private ChatMessage generateMessage() {
        String userId = String.valueOf(random.nextInt(ClientConfig.MAX_USER_ID) + 1);
        String username = "user" + userId;
        String message = ClientConfig.MESSAGES_POOL[random.nextInt(ClientConfig.MESSAGES_POOL.length)];
        String timestamp = java.time.Instant.now().toString();

        // Determine message type based on defined probabilities
        int randomValue = random.nextInt(100);
        MessageType messageType;
        if  (randomValue < ClientConfig.TEXT_RATIO) {
            messageType = MessageType.TEXT;
        } else if (randomValue < ClientConfig.TEXT_RATIO + ClientConfig.JOIN_RATIO) {
            messageType = MessageType.JOIN;
        } else {
            messageType = MessageType.LEAVE;
        }

        int roomId = random.nextInt(ClientConfig.NUM_ROOMS) + 1;

        return new ChatMessage(userId, username, message, timestamp, messageType, roomId);
    }
}
