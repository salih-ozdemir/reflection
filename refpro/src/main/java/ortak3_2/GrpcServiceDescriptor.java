package ortak3_2;


import com.google.common.base.Preconditions;
import io.grpc.Channel;
import io.grpc.stub.AbstractStub;
import java.util.function.Function;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Describes how to create different types of stubs for a specific gRPC service.
 * This encapsulates the gRPC-specific stub creation logic.
 */
public class GrpcServiceDescriptor {
    private final String serviceName; // E.g., "MyService"
    private final Map<Class<? extends AbstractStub<?>>, Function<Channel, ? extends AbstractStub<?>>> stubFactories;

    private GrpcServiceDescriptor(String serviceName) {
        this.serviceName = Preconditions.checkNotNull(serviceName, "Service name cannot be null");
        this.stubFactories = new ConcurrentHashMap<>();
    }

    public static GrpcServiceDescriptor of(String serviceName) {
        return new GrpcServiceDescriptor(serviceName);
    }

    /**
     * Registers a factory for a specific stub type.
     * @param stubClass The class of the stub (e.g., MyServiceGrpc.MyServiceFutureStub.class)
     * @param factory The function to create the stub from a gRPC Channel (e.g., MyServiceGrpc.newFutureStub::new)
     * @param <T> The type of the stub
     * @return This GrpcServiceDescriptor instance for chaining
     */
    public <T extends AbstractStub<T>> GrpcServiceDescriptor registerStubFactory(
            Class<T> stubClass, Function<Channel, T> factory) {
        Preconditions.checkNotNull(stubClass, "Stub class cannot be null");
        Preconditions.checkNotNull(factory, "Factory cannot be null");
        stubFactories.put(stubClass, factory);
        return this;
    }

    /**
     * Creates a stub of the specified type using the registered factory.
     * @param stubClass The class of the stub to create
     * @param channel The gRPC Channel to use
     * @param <T> The type of the stub
     * @return The created stub
     * @throws IllegalArgumentException If no factory is registered for the given stub class
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractStub<T>> T createStub(Class<T> stubClass, Channel channel) {
        Function<Channel, ? extends AbstractStub<?>> factory = stubFactories.get(stubClass);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for stub class: " + stubClass.getName());
        }
        return (T) factory.apply(channel);
    }

    public String getServiceName() {
        return serviceName;
    }
}