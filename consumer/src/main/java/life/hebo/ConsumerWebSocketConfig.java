package life.hebo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ConsumerWebSocketConfig implements WebSocketConfigurer {

    private final RoomManager roomManager;

    public ConsumerWebSocketConfig(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(consumerWebSocketHandler(), "/chat/{roomId}")
                .setAllowedOrigins("*");
    }

    @Bean
    public ConsumerWebSocketHandler consumerWebSocketHandler() {
        return new ConsumerWebSocketHandler(roomManager);
    }
}
