package life.hebo;

import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class ConnectionManager {

    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<PooledConnection>> pool = new ConcurrentHashMap<>();
    private final MetricsCollector metrics;
    private final int connectionsPerRoom;

    public PooledConnection borrowConnection(int roomId) throws Exception {
        CopyOnWriteArrayList<PooledConnection> roomPool = pool.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());

        for (PooledConnection pc : roomPool) {
            if (pc.isOpen() && pc.tryAcquire()) {
                return pc;
            }
        }

        // Create a new connection if pool is not full
        if (roomPool.size() < connectionsPerRoom) {
            PooledConnection pc = createConnection(roomId);
            roomPool.add(pc);
            pc.tryAcquire();
            return pc;
        }

        // Reconnect a broken connection
        for (PooledConnection pc : roomPool) {
            if (!pc.isOpen() && pc.tryAcquire()) {
                reconnect(pc);
                metrics.incrementReconnections();
                return pc;
            }
        }

        return null;
    }

    public void returnConnection(PooledConnection pc) {
        pc.release();
    }

    public void reconnect(PooledConnection pc) throws Exception {
        try {
            if (pc.getSession() != null && pc.getSession().isOpen()) {
                pc.getSession().close();
            }
        } catch (IOException ignored) {}

        connectSession(pc);
    }

    public void closeAll() {
        pool.values().forEach(list -> list.forEach(pc -> {
            try {
                if (pc.getSession() != null && pc.getSession().isOpen()) {
                    pc.getSession().close();
                }
            } catch (IOException ignored) {}
        }));
        pool.clear();
    }

    private PooledConnection createConnection(int roomId) throws Exception {
        PooledConnection pc = new PooledConnection(roomId);
        connectSession(pc);
        metrics.incrementConnections();
        return pc;
    }

    private void connectSession(PooledConnection pc) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create(ClientConfig.SERVER_URI + pc.getRoomId());

        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession sess, TextMessage msg) {
                pc.completeResponse(msg.getPayload());
            }

            @Override
            public void handleTransportError(WebSocketSession sess, Throwable ex) {
                pc.completeExceptionally(ex);
            }

        }, uri.toString()).get(5, TimeUnit.SECONDS);

        pc.setSession(session);
    }

    @RequiredArgsConstructor
    public class PooledConnection {

        private volatile WebSocketSession session;
        private volatile CompletableFuture<String> pendingResponse;
        private final AtomicBoolean inUse = new AtomicBoolean(false);
        private final int roomId;

        public int getRoomId() {
            return roomId;
        }

        public boolean isOpen() {
            return session != null && session.isOpen();
        }

        public boolean tryAcquire() {
            return inUse.compareAndSet(false, true);
        }

        public void release() {
            inUse.set(false);
        }

        public CompletableFuture<String> prepareForResponse() {
            pendingResponse = new CompletableFuture<>();
            return pendingResponse;
        }

        public void completeResponse(String payload) {
            CompletableFuture<String> f = pendingResponse;
            if (f != null) f.complete(payload);
        }

        public void completeExceptionally(Throwable t) {
            CompletableFuture<String> f = pendingResponse;
            if (f != null) f.completeExceptionally(t);
        }

        public void send(String json) throws IOException {
            session.sendMessage(new TextMessage(json));
        }

        public void setSession(WebSocketSession s) {
            this.session = s;
        }

        public WebSocketSession getSession() {
            return session;
        }
    }

}