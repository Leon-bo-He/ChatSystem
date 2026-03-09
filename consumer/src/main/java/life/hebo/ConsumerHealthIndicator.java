package life.hebo;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ConsumerHealthIndicator implements HealthIndicator {

    private final RoomManager roomManager;
    private volatile boolean consumerRunning = true;

    public ConsumerHealthIndicator(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    public void setConsumerRunning(boolean running) {
        this.consumerRunning = running;
    }

    @Override
    public Health health() {
        Health.Builder builder = consumerRunning ? Health.up() : Health.down();
        return builder
                .withDetail("activeRooms", roomManager.getActiveRoomCount())
                .withDetail("activeUsers", roomManager.getActiveUserCount())
                .withDetail("messagesProcessed", roomManager.getMessagesProcessed())
                .withDetail("deliveryFailures", roomManager.getDeliveryFailures())
                .build();
    }
}
