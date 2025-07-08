package grpc2;

// Common Microservice Client Library (Your Library's Core)

public class GrpcChannelManager {

    private final Map<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();
    private final List<ClientInterceptor> globalInterceptors = new ArrayList<>();

    public GrpcChannelManager() {
        // Add common interceptors here, e.g., for logging, metrics, or custom retries
        globalInterceptors.add(new CustomRetryInterceptor());
        // globalInterceptors.add(new TracingClientInterceptor()); // If you use OpenTelemetry/Zipkin
    }

    // Get or create a ManagedChannel for a given address
    public ManagedChannel getOrCreateChannel(String targetAddress) {
        return channelCache.computeIfAbsent(targetAddress, addr -> {
            System.out.println("Creating new ManagedChannel for: " + addr);
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(addr)
                    .usePlaintext(); // Or use .useTransportSecurity() for TLS/SSL

            // Apply all global interceptors
            builder.intercept(globalInterceptors);

            ManagedChannel channel = builder.build();

            // Add a listener to monitor channel state if needed for advanced re-connection logic
            // channel.notifyWhenStateChanged(ConnectivityState.READY, () -> System.out.println("Channel ready!"));
            // channel.notifyWhenStateChanged(ConnectivityState.SHUTDOWN, () -> System.out.println("Channel shutdown!"));

            return channel;
        });
    }

    // Generic method to get a gRPC stub
    public <T extends AbstractStub<T>> T getStub(String serviceAddress, StubFactory<T> factory) {
        ManagedChannel channel = getOrCreateChannel(serviceAddress);
        return factory.create(channel);
    }

    // Interface for creating stubs (to be implemented by service-specific classes)
    @FunctionalInterface
    public interface StubFactory<T extends AbstractStub<T>> {
        T create(ManagedChannel channel);
    }

    // Call this method on application shutdown to gracefully close channels
    public void shutdownAllChannels() {
        System.out.println("Shutting down all gRPC channels...");
        channelCache.values().forEach(channel -> {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.err.println("Channel shutdown interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        });
        channelCache.clear();
    }
}

// ---
