package life.hebo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(ConsumerProperties.class)
public class ConsumerAppConfig {

    @Bean
    public RoomManager roomManager() {
        return new RoomManager();
    }
}
