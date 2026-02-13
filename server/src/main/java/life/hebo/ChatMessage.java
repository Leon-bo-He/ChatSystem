package life.hebo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {

    private String userId;
    private String username;
    private String message;
    private String timestamp;
    private MessageType messageType;

    @JsonProperty(required = false)
    private int roomId;

}
