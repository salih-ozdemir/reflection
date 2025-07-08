package ortak4_1;


import io.grpc.ClientInterceptor;
import java.util.List;
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
        t.setDaemon(true);
        t.setName("connection-reconnect-scheduler");
        return t;
    });

    // Yeni: Servis proxy'lerini (soyut istemcileri) saklamak için
    // Key: Servis adı, Value: Servis proxy'sinin fabrika metodu
    private final Map<String, Function<GrpcManagedConnection, ? extends AsyncServiceClient>> grpcServiceProxies = new ConcurrentHashMap<>();

    private final List<ClientInterceptor> globalGrpcInterceptors;

    public UniversalConnectionManager(List<ClientInterceptor> globalGrpcInterceptors) {
        this.globalGrpcInterceptors = globalGrpcInterceptors;

        reconnectionScheduler.scheduleWithFixedDelay(() -> {
            activeConnections.forEach((id, conn) -> {
                if (!conn.isConnected()) {
                    logger.warn("Connection '{}' detected as disconnected. Attempting to reconnect...", id);
                    try {
                        conn.connect();
                        if (conn.isConnected()) {
                            logger.info("Successfully reconnected to '{}'.", id);
                        }
                    } catch (ConnectionException e) {
                        logger.error("Failed to reconnect '{}': {}", id, e.getMessage());
                    }
                }
            });
        }, 5, 10, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAllConnections));
    }

    // --- Bağlantı Yönetimi Metotları (Öncekiyle aynı) ---
    @FunctionalInterface
    public interface ConnectionFactory<T extends ManagedConnection> {
        T create(String identifier);
    }

    public <T extends ManagedConnection> T getOrCreateConnection(String identifier, ConnectionFactory<T> factory) throws ConnectionException {
        return (T) activeConnections.computeIfAbsent(identifier, id -> {
            logger.info("Creating new connection for: {}", id);
            T newConnection = factory.create(id);
            try {
                newConnection.connect();
            } catch (ConnectionException e) {
                logger.error("Initial connection failed for '{}': {}", id, e.getMessage());
                throw new RuntimeException(e);
            }
            return newConnection;
        });
    }

    public <T extends ManagedConnection> T getConnection(String identifier, Class<T> connectionType) {
        ManagedConnection conn = activeConnections.get(identifier);
        if (conn != null && connectionType.isInstance(conn)) {
            return connectionType.cast(conn);
        }
        return null;
    }

    // --- Yeni: Asenkron Servis Proxy Kayıt ve Alma Metotları ---

    /**
     * Registers an asynchronous service proxy factory.
     * This factory knows how to create a specific AsyncServiceClient (e.g., MyServiceAsyncClient)
     * using a GrpcManagedConnection.
     *
     * @param serviceName The logical name of the service (e.g., "MyService").
     * @param proxyFactory A function that takes a GrpcManagedConnection and returns an instance of AsyncServiceClient.
     * @param <T> The type of the AsyncServiceClient.
     */
    public <T extends AsyncServiceClient> void registerAsyncServiceProxy(
            String serviceName, Function<GrpcManagedConnection, T> proxyFactory) {
        grpcServiceProxies.put(serviceName, proxyFactory);
        logger.info("Registered async service proxy for: {}", serviceName);
    }

    /**
     * Retrieves a specific asynchronous service client (proxy) instance.
     * The application only needs to provide the service name and the target address.
     * The actual gRPC stub creation is handled internally.
     *
     * @param serviceName The logical name of the gRPC service (e.g., "MyService").
     * @param grpcTargetAddress The address of the gRPC server (e.g., "localhost:50051").
     * @param serviceClientClass The class of the desired asynchronous service client (e.g., MyServiceAsyncClient.class).
     * @param <T> The type of the AsyncServiceClient.
     * @return An instance of the requested asynchronous service client.
     * @throws IllegalArgumentException If the service or its proxy factory is not registered.
     * @throws ConnectionException If the underlying gRPC connection cannot be established.
     * @throws IllegalStateException If the gRPC channel is not connected.
     */
    @SuppressWarnings("unchecked")
    public <T extends AsyncServiceClient> T getAsyncServiceClient(
            String serviceName, String grpcTargetAddress, Class<T> serviceClientClass)
            throws ConnectionException {

        // 1. gRPC bağlantısını al veya oluştur
        GrpcManagedConnection grpcConn = getOrCreateConnection(
                grpcTargetAddress,
                id -> new GrpcManagedConnection(id, globalGrpcInterceptors)
        );

        // 2. Servis proxy fabrika metodunu al
        Function<GrpcManagedConnection, ? extends AsyncServiceClient> proxyFactory = grpcServiceProxies.get(serviceName);
        if (proxyFactory == null) {
            throw new IllegalArgumentException("No async service proxy registered for: " + serviceName);
        }

        // 3. Fabrika ile asenkron servis istemcisini oluştur
        AsyncServiceClient client = proxyFactory.apply(grpcConn);

        if (!serviceClientClass.isInstance(client)) {
            throw new ClassCastException("Expected client of type " + serviceClientClass.getName() + " but got " + client.getClass().getName());
        }
        return (T) client;
    }

    // --- Genel Kapatma Metodu (Öncekiyle aynı) ---
    public void shutdownAllConnections() {
        logger.info("Shutting down all managed connections...");
        reconnectionScheduler.shutdownNow();
        activeConnections.values().forEach(ManagedConnection::disconnect);
        activeConnections.clear();
        logger.info("All managed connections shut down.");
    }
}