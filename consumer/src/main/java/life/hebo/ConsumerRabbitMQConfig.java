package life.hebo;

import com.rabbitmq.client.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class ConsumerRabbitMQConfig {

    @Bean
    public ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();

        String host = Optional.ofNullable(System.getenv("RABBITMQ_HOST")).orElse("localhost");
        factory.setHost(host);

        String portStr = System.getenv("RABBITMQ_PORT");
        if (portStr != null) {
            try {
                factory.setPort(Integer.parseInt(portStr));
            } catch (NumberFormatException ignored) {}
        }

        String username = System.getenv("RABBITMQ_USERNAME");
        if (username != null && !username.isEmpty()) {
            factory.setUsername(username);
        }

        String password = System.getenv("RABBITMQ_PASSWORD");
        if (password != null && !password.isEmpty()) {
            factory.setPassword(password);
        }

        return factory;
    }
}
