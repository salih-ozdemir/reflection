package ortak;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class GrpcManagedConnection implements ManagedConnection {
    private final String targetAddress;
    private ManagedChannel channel;
    private final List<ClientInterceptor> interceptors; // To apply common interceptors

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
        // gRPC channel state can be more nuanced. READY or IDLE usually means connected.
        return channel != null && (channel.getState(true) == ConnectivityState.READY || channel.getState(true) == ConnectivityState.IDLE);
    }

    @Override
    public void connect() throws ConnectionException {
        if (channel == null || channel.isShutdown() || channel.isTerminated()) {
            System.out.println("Connecting gRPC to: " + targetAddress);
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(targetAddress)
                    .usePlaintext(); // Or use .useTransportSecurity() for TLS/SSL

            // Apply global/common interceptors
            if (interceptors != null) {
                builder.intercept(interceptors);
            }

            channel = builder.build();
            // You might want to block until connected for initial setup
            // channel.awaitTermination(timeout, unit); // Or use notifyWhenStateChanged for async handling
        }
        // If already connected, do nothing
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
                channel = null; // Clear the channel reference
            }
        }
    }

    public ManagedChannel getChannel() {
        // Provide access to the underlying gRPC channel for stub creation
        return channel;
    }
}