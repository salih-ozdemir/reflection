package ortak3_1;

import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.AbstractStub;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcManagedConnection implements ManagedConnection {
    private static final Logger logger = LoggerFactory.getLogger(GrpcManagedConnection.class);

    private final String targetAddress;
    private ManagedChannel channel;
    private final List<ClientInterceptor> interceptors;

    public GrpcManagedConnection(String targetAddress, List<ClientInterceptor> interceptors) {
        this.targetAddress = targetAddress;
        this.interceptors = interceptors;
    }

    @Override
    public String getIdentifier() {
        return targetAddress;
    }

    @Override
    public boolean isConnected() {
        return channel != null && (channel.getState(true) == ConnectivityState.READY || channel.getState(true) == ConnectivityState.IDLE);
    }

    @Override
    public void connect() throws ConnectionException {
        if (channel == null || channel.isShutdown() || channel.isTerminated()) {
            logger.info("Connecting gRPC to: {}", targetAddress);
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(targetAddress)
                    .usePlaintext(); // Production'da .useTransportSecurity() kullanın

            if (interceptors != null) {
                builder.intercept(interceptors);
            }

            channel = builder.build();
        }
    }

    @Override
    public void disconnect() {
        if (channel != null && !channel.isShutdown() && !channel.isTerminated()) {
            logger.info("Disconnecting gRPC from: {}", targetAddress);
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("gRPC channel shutdown interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                channel = null;
            }
        }
    }

    // Existing getStub method (can still be used for lower-level access if needed)
    public <T extends AbstractStub<T>> T getStub(GrpcStubFactory<T> factory) {
        if (channel == null) {
            throw new IllegalStateException("gRPC channel is not connected. Call connect() first.");
        }
        return factory.create(channel);
    }

    /**
     * Provides a type-safe way to get a specific gRPC service client (stub).
     * The application only needs to provide the class type of the desired stub
     * and the factory method to create it.
     *
     * @param serviceClientClass The class of the desired gRPC stub (e.g., MyServiceGrpc.MyServiceFutureStub.class)
     * @param stubFactory A lambda or method reference to create the stub from a Channel (e.g., MyServiceGrpc.newFutureStub::new)
     * @param <T> The type of the gRPC stub
     * @return An instance of the requested gRPC stub
     * @throws IllegalStateException If the gRPC channel is not connected
     * @throws ClassCastException If the created stub is not of the expected type (should not happen with correct factory)
     */
    public <T extends AbstractStub<T>> T getServiceClient(Class<T> serviceClientClass, GrpcStubFactory<T> stubFactory) {
        if (channel == null) {
            throw new IllegalStateException("gRPC channel is not connected. Call connect() first.");
        }
        // This is where the magic happens: we use the provided factory
        // to create the stub, ensuring the application doesn't call newFutureStub::new directly.
        T client = stubFactory.create(channel);
        if (!serviceClientClass.isInstance(client)) {
            throw new ClassCastException("Expected client of type " + serviceClientClass.getName() + " but got " + client.getClass().getName());
        }
        return client;
    }
}