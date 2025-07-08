package ortak2;

// --- Modified GrpcManagedConnection ---
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import java.util.concurrent.TimeUnit;
import java.util.List;
import io.grpc.stub.AbstractStub; // Import AbstractStub

public class GrpcManagedConnection implements ManagedConnection {
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
            System.out.println("Connecting gRPC to: " + targetAddress);
            // Use ManagedChannelBuilder directly, no need for NettyChannelBuilder here
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(targetAddress)
                    .usePlaintext(); // Or use .useTransportSecurity() for TLS/SSL

            if (interceptors != null) {
                builder.intercept(interceptors);
            }

            channel = builder.build();
        }
    }

    @Override
    public void disconnect() {
        if (channel != null && !channel.isShutdown() && !channel.isTerminated()) {
            System.out.println("Disconnecting gRPC from: " + targetAddress);
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.err.println("gRPC channel shutdown interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                channel = null;
            }
        }
    }

    // New method: Provides a generic way to get a gRPC stub
    public <T extends AbstractStub<T>> T getStub(GrpcStubFactory<T> factory) {
        if (channel == null) {
            throw new IllegalStateException("gRPC channel is not connected. Call connect() first.");
        }
        return factory.create(channel);
    }
}