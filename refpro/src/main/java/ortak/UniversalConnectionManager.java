package ortak;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class UniversalConnectionManager {

    private final Map<String, ManagedConnection> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor();

    public UniversalConnectionManager() {
        // Start a background task to periodically check and reconnect
        reconnectionScheduler.scheduleWithFixedDelay(() -> {
            activeConnections.forEach((id, conn) -> {
                if (!conn.isConnected()) {
                    System.out.println("Connection " + id + " detected as disconnected. Attempting to reconnect...");
                    try {
                        conn.connect(); // Attempt to reconnect
                    } catch (ConnectionException e) {
                        System.err.println("Failed to reconnect " + id + ": " + e.getMessage());
                        // Implement more sophisticated retry policies (e.g., exponential backoff)
                    }
                }
            });
        }, 5, 10, TimeUnit.SECONDS); // Check every 10 seconds, start after 5 seconds

        // Register JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAllConnections));
    }

    public <T extends ManagedConnection> T getOrCreateConnection(String identifier, ConnectionFactory<T> factory) throws ConnectionException {
        // Use computeIfAbsent for thread-safe lazy initialization
        return (T) activeConnections.computeIfAbsent(identifier, id -> {
            System.out.println("Creating new connection: " + id);
            T newConnection = factory.create(id);
            try {
                newConnection.connect(); // Initial connection attempt
            } catch (ConnectionException e) {
                System.err.println("Initial connection failed for " + id + ": " + e.getMessage());
                // Decide how to handle initial failure: rethrow, return null, or keep trying
                // For now, re-throw to indicate initial failure
                throw new RuntimeException(e); // Wrapping for lambda
            }
            return newConnection;
        });
    }

    // Generic factory interface for creating different connection types
    @FunctionalInterface
    public interface ConnectionFactory<T extends ManagedConnection> {
        T create(String identifier);
    }

    // Shutdown all managed connections gracefully
    public void shutdownAllConnections() {
        System.out.println("Shutting down all managed connections...");
        reconnectionScheduler.shutdownNow(); // Stop the background reconnector
        activeConnections.values().forEach(ManagedConnection::disconnect);
        activeConnections.clear();
    }

    // Helper to get a specific type of connection if it exists
    public <T extends ManagedConnection> T getConnection(String identifier, Class<T> connectionType) {
        ManagedConnection conn = activeConnections.get(identifier);
        if (conn != null && connectionType.isInstance(conn)) {
            return connectionType.cast(conn);
        }
        return null;
    }
}