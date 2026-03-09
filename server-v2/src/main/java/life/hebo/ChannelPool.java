package life.hebo;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class ChannelPool {

    private final BlockingQueue<Channel> pool;
    private final ConnectionFactory connectionFactory;
    private final int poolSize;

    private volatile Connection connection;

    public ChannelPool(ConnectionFactory connectionFactory, int poolSize) {
        this.connectionFactory = connectionFactory;
        this.poolSize = poolSize;
        this.pool = new ArrayBlockingQueue<>(poolSize);
    }

    public synchronized void init() throws IOException, TimeoutException {
        if (connection != null && connection.isOpen()) {
            return;
        }

        this.connection = connectionFactory.newConnection();
        for (int i = 0; i < poolSize; i++) {
            pool.offer(connection.createChannel());
        }
    }

    public Channel borrowChannel() throws IOException, TimeoutException, InterruptedException {
        ensureConnection();

        Channel channel = pool.poll();
        if (channel == null || !channel.isOpen()) {
            channel = connection.createChannel();
        }
        return channel;
    }

    public void returnChannel(Channel channel) {
        if (channel == null) {
            return;
        }

        if (!channel.isOpen() || !pool.offer(channel)) {
            try {
                channel.close();
            } catch (Exception ignored) {
            }
        }
    }

    private synchronized void ensureConnection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            init();
        }
    }
}

