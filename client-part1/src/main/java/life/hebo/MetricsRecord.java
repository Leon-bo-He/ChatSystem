package life.hebo;


import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class MetricsRecord {

    private final long sendTimestamp;
    private final MessageType messageType;
    private final long latencyMs;
    private final int statusCode; // 200 = success, 0 = failure
    private final int roomId;
}
