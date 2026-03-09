package life.hebo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueueMessage {

    private String messageId;
    private String roomId;
    private String userId;
    private String username;
    private String message;
    private String timestamp;
    private MessageType messageType;
    private String serverId;
    private String clientIp;



}
