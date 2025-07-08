package ortak3_2;

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

    // Mevcut bağlantı yönetimi
    private final Map<String, ManagedConnection> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        t.setName("connection-reconnect-scheduler");
        return t;
    });

    // Yeni: gRPC servis tanımlayıcılarını saklamak için
    private final Map<String, GrpcServiceDescriptor> grpcServiceDescriptors = new ConcurrentHashMap<>();
    private final List<ClientInterceptor> globalGrpcInterceptors; // Ortak interceptor'lar

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

    // --- Yeni: gRPC Servis Kayıt ve Alma Metotları ---

    /**
     * Registers a GrpcServiceDescriptor for a specific gRPC service.
     * This allows the manager to create specific stubs later without direct application involvement.
     * @param descriptor The GrpcServiceDescriptor for the service.
     */
    public void registerGrpcService(GrpcServiceDescriptor descriptor) {
        grpcServiceDescriptors.put(descriptor.getServiceName(), descriptor);
        logger.info("Registered gRPC service descriptor for: {}", descriptor.getServiceName());
    }

    /**
     * Retrieves a specific gRPC service client (stub) instance.
     * The application only needs to provide the service name and the desired stub type.
     *
     * @param serviceName The logical name of the gRPC service (e.g., "MyService").
     * @param grpcTargetAddress The address of the gRPC server (e.g., "localhost:50051").
     * @param stubClass The class of the desired gRPC stub (e.g., MyServiceGrpc.MyServiceFutureStub.class).
     * @param <T> The type of the gRPC stub.
     * @return An instance of the requested gRPC stub.
     * @throws IllegalArgumentException If the service or stub type is not registered.
     * @throws ConnectionException If the underlying gRPC connection cannot be established.
     * @throws IllegalStateException If the gRPC channel is not connected.
     */
    public <T extends AbstractStub<T>> T getGrpcServiceClient(
            String serviceName, String grpcTargetAddress, Class<T> stubClass)
            throws ConnectionException {

        // 1. gRPC bağlantısını al veya oluştur
        GrpcManagedConnection grpcConn = getOrCreateConnection(
                grpcTargetAddress,
                id -> new GrpcManagedConnection(id, globalGrpcInterceptors)
        );

        // 2. Servis açıklayıcısını al
        GrpcServiceDescriptor descriptor = grpcServiceDescriptors.get(serviceName);
        if (descriptor == null) {
            throw new IllegalArgumentException("No gRPC service descriptor registered for: " + serviceName);
        }

        // 3. Kanal üzerinden istenen stub'ı oluştur
        return descriptor.createStub(stubClass, grpcConn.getChannel());
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