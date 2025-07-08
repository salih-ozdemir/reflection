package ortak3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UniversalConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(UniversalConnectionManager.class);

    private final Map<String, ManagedConnection> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true); // JVM çıkışında thread'in otomatik kapanmasını sağlar
        t.setName("connection-reconnect-scheduler");
        return t;
    });

    public UniversalConnectionManager() {
        // Arka planda periyodik olarak bağlantı durumunu kontrol et ve yeniden bağlan
        reconnectionScheduler.scheduleWithFixedDelay(() -> {
            activeConnections.forEach((id, conn) -> {
                if (!conn.isConnected()) {
                    logger.warn("Connection '{}' detected as disconnected. Attempting to reconnect...", id);
                    try {
                        conn.connect(); // Yeniden bağlanma denemesi
                        if (conn.isConnected()) {
                            logger.info("Successfully reconnected to '{}'.", id);
                        }
                    } catch (ConnectionException e) {
                        logger.error("Failed to reconnect '{}': {}", id, e.getMessage());
                        // Burada daha sofistike retry politikaları (örn: exponential backoff) uygulanabilir.
                    }
                }
            });
        }, 5, 10, TimeUnit.SECONDS); // 5 saniye sonra başla, her 10 saniyede bir kontrol et

        // JVM kapatıldığında tüm bağlantıları düzgünce kapatmak için shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAllConnections));
    }

    // Generic factory interface for creating different connection types
    @FunctionalInterface
    public interface ConnectionFactory<T extends ManagedConnection> {
        T create(String identifier);
    }

    public <T extends ManagedConnection> T getOrCreateConnection(String identifier, ConnectionFactory<T> factory) throws ConnectionException {
        // ComputeIfAbsent, thread-safe lazy initialization için kullanılır
        return (T) activeConnections.computeIfAbsent(identifier, id -> {
            logger.info("Creating new connection for: {}", id);
            T newConnection = factory.create(id);
            try {
                newConnection.connect(); // İlk bağlantı denemesi
            } catch (ConnectionException e) {
                logger.error("Initial connection failed for '{}': {}", id, e.getMessage());
                // İlk bağlantı başarısız olursa RuntimeException fırlatırız
                throw new RuntimeException(e);
            }
            return newConnection;
        });
    }

    public void shutdownAllConnections() {
        logger.info("Shutting down all managed connections...");
        reconnectionScheduler.shutdownNow(); // Yeniden bağlantı planlayıcısını durdur
        activeConnections.values().forEach(ManagedConnection::disconnect);
        activeConnections.clear();
        logger.info("All managed connections shut down.");
    }

    public <T extends ManagedConnection> T getConnection(String identifier, Class<T> connectionType) {
        ManagedConnection conn = activeConnections.get(identifier);
        if (conn != null && connectionType.isInstance(conn)) {
            return connectionType.cast(conn);
        }
        return null;
    }
}