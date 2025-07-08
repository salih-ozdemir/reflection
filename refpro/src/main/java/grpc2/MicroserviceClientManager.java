package grpc2;

public class MicroserviceClientManager {
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    // ... (connection management logic)

    public <T extends AbstractStub<T>> T getClient(String serviceAddress, ChannelStubFactory<T> factory) {
        ManagedChannel channel = getOrCreateChannel(serviceAddress); // Your channel management logic
        return factory.createStub(channel);
    }

    // Interface to abstract stub creation
    public interface ChannelStubFactory<T extends AbstractStub<T>> {
        T createStub(ManagedChannel channel);
    }

    // Example Usage:
    // YourServiceClient client = manager.getClient("localhost:50051", YourServiceGrpc.newBlockingStub::new);
}