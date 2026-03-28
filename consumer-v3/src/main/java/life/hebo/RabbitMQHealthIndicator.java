package life.hebo;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQHealthIndicator implements HealthIndicator {

    private final ConnectionFactory connectionFactory;

    public RabbitMQHealthIndicator(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try {
            try (Connection conn = connectionFactory.newConnection()) {
                if (conn.isOpen()) {
                    return Health.up().withDetail("host", connectionFactory.getHost()).build();
                }
            }
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
        return Health.down().build();
    }
}
