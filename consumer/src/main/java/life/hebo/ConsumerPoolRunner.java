package life.hebo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ConsumerPoolRunner implements CommandLineRunner {

    private final ConnectionFactory connectionFactory;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final ConsumerHealthIndicator healthIndicator;
    private final int workerCount;

    private volatile ConsumerPool consumerPool;
    private volatile Thread consumerThread;

    public ConsumerPoolRunner(ConnectionFactory connectionFactory,
                              RoomManager roomManager,
                              ObjectMapper objectMapper,
                              ConsumerHealthIndicator healthIndicator,
                              @Value("${app.consumer.worker-threads:4}") int workerCount) {
        this.connectionFactory = connectionFactory;
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
        this.healthIndicator = healthIndicator;
        this.workerCount = workerCount;
    }

    @Override
    public void run(String... args) {
        consumerPool = new ConsumerPool(connectionFactory, roomManager, objectMapper, workerCount);
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
