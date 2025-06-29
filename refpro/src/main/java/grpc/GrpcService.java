package grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ConnectivityState;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

// Assuming your generated gRPC service interface is named MyServiceGrpc
// and your request/response messages are MyRequest and MyResponse
// import com.example.grpc.MyServiceGrpc;
// import com.example.grpc.MyServiceProto.MyRequest;
// import com.example.grpc.MyServiceProto.MyResponse;

@Service
public class GrpcService {

    private static final Logger log = LoggerFactory.getLogger(GrpcService.class);

    @Value("${grpc.server.host:localhost}")
    private String grpcHost;

    @Value("${grpc.server.port:50051}")
    private int grpcPort;

    @Value("${grpc.reconnect.initial-delay-ms:1000}")
    private long reconnectInitialDelayMs;

    @Value("${grpc.reconnect.max-delay-ms:60000}")
    private long reconnectMaxDelayMs;

    private ManagedChannel channel;
    private MyServiceGrpc.MyServiceStub asyncStub; // For asynchronous calls

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private long currentReconnectDelay = 0;

    public GrpcService() {
        // Initialize currentReconnectDelay
        this.currentReconnectDelay = reconnectInitialDelayMs;
    }

    // Initialize the gRPC channel and stub after properties are set
    @jakarta.annotation.PostConstruct
    public void init() {
        connect();
    }

    private synchronized void connect() {
        if (channel != null && !channel.isShutdown() && !channel.isTerminated()) {
            log.info("gRPC channel already connected or in process of connecting.");
            return;
        }

        log.info("Attempting to connect to gRPC server at {}:{}", grpcHost, grpcPort);
        try {
            channel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
                    .usePlaintext() // For development; use .useTransportSecurity() for production
                    .keepAliveTime(10, TimeUnit.SECONDS) // Send pings to keep connection alive
                    .keepAliveTimeout(5, TimeUnit.SECONDS) // Disconnect if no response to ping
                    .keepAliveWithoutCalls(true) // Send pings even if no active calls
                    .build();

            // Monitor connectivity state changes
            channel.notifyWhenStateChanged(ConnectivityState.READY, () -> {
                log.info("gRPC channel state changed to READY.");
                if (reconnecting.getAndSet(false)) { // Reset reconnecting flag if successfully reconnected
                    log.info("Successfully reconnected to gRPC server.");
                    currentReconnectDelay = reconnectInitialDelayMs; // Reset delay on successful reconnect
                }
            });
            channel.notifyWhenStateChanged(ConnectivityState.IDLE, () -> {
                log.warn("gRPC channel state changed to IDLE. Attempting to enter READY state.");
                // This will trigger a connection attempt
                channel.getState(true);
            });
            channel.notifyWhenStateChanged(ConnectivityState.CONNECTING, () -> {
                log.info("gRPC channel state changed to CONNECTING.");
            });
            channel.notifyWhenStateChanged(ConnectivityState.TRANSIENT_FAILURE, () -> {
                log.warn("gRPC channel state changed to TRANSIENT_FAILURE. Reconnecting in {}ms...", currentReconnectDelay);
                scheduleReconnect();
            });
            channel.notifyWhenStateChanged(ConnectivityState.SHUTDOWN, () -> {
                log.error("gRPC channel state changed to SHUTDOWN. This channel is no longer usable.");
            });

            asyncStub = MyServiceGrpc.newStub(channel); // Create the async stub
            log.info("gRPC channel and async stub initialized.");
            reconnecting.set(false); // If we're here, initial connection was successful
            currentReconnectDelay = reconnectInitialDelayMs;
        } catch (Exception e) {
            log.error("Failed to connect to gRPC server at {}:{}: {}", grpcHost, grpcPort, e.getMessage());
            // Initial connection failed, schedule a reconnect
            scheduleReconnect();
        }
    }

    private synchronized void scheduleReconnect() {
        if (reconnecting.compareAndSet(false, true)) { // Only schedule if not already reconnecting
            scheduler.schedule(this::connect, currentReconnectDelay, TimeUnit.MILLISECONDS);
            log.info("Scheduled gRPC reconnection in {}ms.", currentReconnectDelay);
            currentReconnectDelay = Math.min(reconnectMaxDelayMs, currentReconnectDelay * 2); // Exponential backoff
        } else {
            log.debug("Reconnect already scheduled. Skipping new schedule.");
        }
    }

    public ConnectivityState getConnectionState() {
        if (channel == null) {
            return ConnectivityState.SHUTDOWN; // Or some custom "UNINITIALIZED" state
        }
        return channel.getState(false); // false means don't try to connect if IDLE
    }

    public boolean isConnected() {
        return getConnectionState() == ConnectivityState.READY;
    }

    /**
     * Sends an asynchronous message to the gRPC server.
     * This example assumes a unary RPC. For streaming RPCs, the StreamObserver
     * implementation will be more complex.
     *
     * @param request The request message to send.
     * @param responseObserver A StreamObserver to handle the server's response.
     */
    public void sendAsyncMessage(MyRequest request, StreamObserver<MyResponse> responseObserver) {
        if (!isConnected()) {
            log.warn("gRPC connection not READY. Attempting to reconnect and deferring message.");
            // You might want to queue the message or return an error immediately
            // For now, we'll just log and let the observer handle the error if the call fails.
            // Or throw an exception for immediate feedback:
            responseObserver.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("gRPC service is not connected.")));
            return;
        }

        try {
            asyncStub.unaryMethod(request, responseObserver); // Replace unaryMethod with your actual RPC method
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed: {}", e.getStatus().getCode());
            responseObserver.onError(e);
            if (e.getStatus().getCode() == Status.Code.UNAVAILABLE ||
                    e.getStatus().getCode() == Status.Code.UNAUTHENTICATED || // Or other relevant codes
                    e.getStatus().getCode() == Status.Code.UNIMPLEMENTED) { // If server might be down or misconfigured
                log.warn("Detected connection issue, scheduling reconnect.");
                scheduleReconnect();
            }
        } catch (Exception e) {
            log.error("Error during gRPC call: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    /**
     * Example of handling a server-side streaming RPC (server sends multiple responses).
     */
    public void startServerStreaming(MyRequest request, StreamObserver<MyResponse> responseObserver) {
        if (!isConnected()) {
            responseObserver.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("gRPC service is not connected.")));
            return;
        }
        try {
            asyncStub.serverStreamingMethod(request, responseObserver); // Replace with your actual server streaming RPC
        } catch (StatusRuntimeException e) {
            log.error("gRPC server streaming call failed: {}", e.getStatus().getCode());
            responseObserver.onError(e);
            scheduleReconnectIfNeeded(e.getStatus().getCode());
        } catch (Exception e) {
            log.error("Error during gRPC server streaming call: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    /**
     * Example of handling a client-side streaming RPC (client sends multiple requests).
     * Returns a StreamObserver for the client to write requests to.
     */
    public StreamObserver<MyRequest> startClientStreaming(StreamObserver<MyResponse> responseObserver) {
        if (!isConnected()) {
            responseObserver.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("gRPC service is not connected.")));
            return new StreamObserver<MyRequest>() {
                @Override public void onNext(MyRequest value) { /* No-op */ }
                @Override public void onError(Throwable t) { /* No-op */ }
                @Override public void onCompleted() { /* No-op */ }
            };
        }
        try {
            return asyncStub.clientStreamingMethod(responseObserver); // Replace with your actual client streaming RPC
        } catch (StatusRuntimeException e) {
            log.error("gRPC client streaming call failed: {}", e.getStatus().getCode());
            responseObserver.onError(e);
            scheduleReconnectIfNeeded(e.getStatus().getCode());
            return new StreamObserver<MyRequest>() { // Return a dummy observer to prevent NPE
                @Override public void onNext(MyRequest value) { /* No-op */ }
                @Override public void onError(Throwable t) { /* No-op */ }
                @Override public void onCompleted() { /* No-op */ }
            };
        } catch (Exception e) {
            log.error("Error during gRPC client streaming call: {}", e.getMessage(), e);
            responseObserver.onError(e);
            return new StreamObserver<MyRequest>() { // Return a dummy observer to prevent NPE
                @Override public void onNext(MyRequest value) { /* No-op */ }
                @Override public void onError(Throwable t) { /* No-op */ }
                @Override public void onCompleted() { /* No-op */ }
            };
        }
    }

    /**
     * Example of handling a bidirectional streaming RPC.
     * Returns a StreamObserver for the client to write requests to.
     */
    public StreamObserver<MyRequest> startBidiStreaming(StreamObserver<MyResponse> responseObserver) {
        if (!isConnected()) {
            responseObserver.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("gRPC service is not connected.")));
            return new StreamObserver<MyRequest>() {
                @Override public void onNext(MyRequest value) { /* No-op */ }
                @Override public void onError(Throwable t) { /* No-op */ }
                @Override public void onCompleted() { /* No-op */ }
            };
        }
        try {
            return asyncStub.bidirectionalStreamingMethod(responseObserver); // Replace with your actual bidi streaming RPC
        } catch (StatusRuntimeException e) {
            log.error("gRPC bidirectional streaming call failed: {}", e.getStatus().getCode());
            responseObserver.onError(e);
            scheduleReconnectIfNeeded(e.getStatus().getCode());
            return new StreamObserver<MyRequest>() {
                @Override public void onNext(MyRequest value) { /* No-op */ }
                @Override public void onError(Throwable t) { /* No-op */ }
                @Override public void onCompleted() { /* No-op */ }
            };
        } catch (Exception e) {
            log.error("Error during gRPC bidirectional streaming call: {}", e.getMessage(), e);
            responseObserver.onError(e);
            return new StreamObserver<MyRequest>() {
                @Override public void onNext(MyRequest value) { /* No-op */ }
                @Override public void onError(Throwable t) { /* No-op */ }
                @Override public void onCompleted() { /* No-op */ }
            };
        }
    }

    private void scheduleReconnectIfNeeded(Status.Code statusCode) {
        if (statusCode == Status.Code.UNAVAILABLE || statusCode == Status.Code.UNAUTHENTICATED || statusCode == Status.Code.UNKNOWN) {
            log.warn("Detected potential connection issue (status: {}), scheduling reconnect.", statusCode);
            scheduleReconnect();
        }
    }


    @PreDestroy
    public void shutdown() {
        log.info("Shutting down gRPC channel...");
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("gRPC channel shutdown interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                channel.shutdownNow();
            }
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        log.info("gRPC channel shutdown complete.");
    }
}