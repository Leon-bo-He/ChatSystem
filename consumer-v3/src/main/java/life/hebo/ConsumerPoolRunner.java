package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ConsumerPoolRunner implements CommandLineRunner {

    private final ConnectionFactory connectionFactory;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final MessagePersistenceService persistenceService;
    private final ConsumerStatsAggregator statsAggregator;
    private final ConsumerProperties properties;
    private final ConsumerHealthIndicator healthIndicator;

    private volatile ConsumerPool consumerPool;
    private volatile Thread consumerThread;

    public ConsumerPoolRunner(ConnectionFactory connectionFactory,
                              RoomManager roomManager,
                              ObjectMapper objectMapper,
                              MessagePersistenceService persistenceService,
                              ConsumerStatsAggregator statsAggregator,
                              ConsumerProperties properties,
                              ConsumerHealthIndicator healthIndicator) {
        this.connectionFactory = connectionFactory;
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
        this.persistenceService = persistenceService;
        this.statsAggregator = statsAggregator;
        this.properties = properties;
        this.healthIndicator = healthIndicator;
    }

    @Override
    public void run(String... args) {
        consumerPool = new ConsumerPool(connectionFactory, roomManager, objectMapper, persistenceService, statsAggregator, properties);
        consumerThread = new Thread(consumerPool, "consumer-pool-main");
        consumerThread.setDaemon(false);
        consumerThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (consumerPool != null) {
                healthIndicator.setConsumerRunning(false);
                consumerPool.stop();
            }
            if (consumerThread != null) {
                try {
                    consumerThread.join(5000);
                } catch (InterruptedException ignored) {}
            }
        }));
    }
}
