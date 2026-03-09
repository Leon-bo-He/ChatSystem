package life.hebo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerResponse {

    private String status;
    private String serverTimestamp;
    private String message;
}
