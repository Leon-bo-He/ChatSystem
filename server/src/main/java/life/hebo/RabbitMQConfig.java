package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class RabbitMQConfig {

    @Bean
    public ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();

        String host = Optional.ofNullable(System.getenv("RABBITMQ_HOST")).orElse("localhost");
        factory.setHost(host);

        String portStr = System.getenv("RABBITMQ_PORT");
        if (portStr != null) {
            try {
                factory.setPort(Integer.parseInt(portStr));
            } catch (NumberFormatException ignored) {
                // Use default port if parsing fails
            }
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

    @Bean
    public ChannelPool channelPool(ConnectionFactory rabbitConnectionFactory) {
        int poolSize = Optional.ofNullable(System.getenv("RABBITMQ_CHANNEL_POOL_SIZE"))
                .map(Integer::parseInt)
                .orElse(10);

        // Connect lazily on first publish; avoids failing startup when RabbitMQ is unavailable
        return new ChannelPool(rabbitConnectionFactory, poolSize);
    }

    @Bean
    public MessageQueuePublisher messageQueuePublisher(ChannelPool channelPool, ObjectMapper objectMapper) {
        return new MessageQueuePublisher(channelPool, objectMapper);
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler(MessageQueuePublisher messageQueuePublisher) {
        return new ChatWebSocketHandler(messageQueuePublisher);
    }
}

