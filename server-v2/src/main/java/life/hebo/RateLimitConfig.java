package life.hebo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ServerRateLimitProperties.class)
public class RateLimitConfig {
}
