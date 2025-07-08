package ortak3;

import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.AbstractStub;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(targetAddress)
                    .usePlaintext(); // Production'da .useTransportSecurity() kullanın

            if (interceptors != null) {
                builder.intercept(interceptors);
            }

            channel = builder.build();
            // Kanalın bağlanmasını bir süre bekleyebiliriz (isteğe bağlı)
            // try {
            //     channel.awaitTermination(5, TimeUnit.SECONDS); // Bu, blocking bir çağrıdır
            // } catch (InterruptedException e) {
            //     Thread.currentThread().interrupt();
            //     throw new ConnectionException("gRPC initial connection interrupted", e);
            // }
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

    public <T extends AbstractStub<T>> T getStub(GrpcStubFactory<T> factory) {
        if (channel == null) {
            throw new IllegalStateException("gRPC channel is not connected. Call connect() first.");
        }
        return factory.create(channel);
    }
}