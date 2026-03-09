package life.hebo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsumerAppConfig {

    @Bean
    public RoomManager roomManager() {
        return new RoomManager();
    }
}
