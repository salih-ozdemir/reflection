package ortak;

import io.grpc.netty.NettyChannelBuilder; // Example for a specific gRPC builder
import io.grpc.ClientInterceptor;
import java.util.Arrays;
import java.util.Collections;

public class MyUnifiedApplication {
    private static final UniversalConnectionManager connectionManager = new UniversalConnectionManager();

    public static void main(String[] args) {
        // Assume you have a list of common gRPC interceptors
        List<ClientInterceptor> commonGrpcInterceptors = Arrays.asList(new CustomRetryInterceptor()); // Your custom retry interceptor from previous example

        // --- Managing gRPC Connections ---
        try {
            // Get or create a gRPC connection
            GrpcManagedConnection grpcConn = connectionManager.getOrCreateConnection(
                    "localhost:50051",
                    id -> new GrpcManagedConnection(id, commonGrpcInterceptors)
            );

            // Use the underlying gRPC channel to create a stub
            MyServiceGrpc.MyServiceBlockingStub grpcClient = MyServiceGrpc.newBlockingStub(grpcConn.getChannel());

            // Make an RPC call
            System.out.println("Making gRPC call...");
            MyProto.MyResponse response = grpcClient.myMethod(MyProto.MyRequest.newBuilder().setName("Unified").build());
            System.out.println("gRPC Response: " + response.getMessage());

        } catch (RuntimeException | ConnectionException e) { // RuntimeException for lambda wrapping
            System.err.println("Error with gRPC connection/call: " + e.getMessage());
        }

        // --- Managing Ethernet Connections ---
        try {
            // Get or create an Ethernet connection
            EthernetManagedConnection ethConn = connectionManager.getOrCreateConnection(
                    "ethernet-device:12345", // A unique identifier for your Ethernet connection
                    id -> {
                        // Parse host and port from the identifier for Ethernet
                        String[] parts = id.split(":");
                        return new EthernetManagedConnection(parts[0], Integer.parseInt(parts[1]));
                    }
            );

            // Use the underlying socket for Ethernet communication
            if (ethConn.isConnected()) {
                System.out.println("Ethernet is connected. Socket local port: " + ethConn.getSocket().getLocalPort());
                // You can now get ethConn.getSocket().getInputStream() and .getOutputStream()
                // and interact with your Ethernet device.
            } else {
                System.out.println("Ethernet connection is not active.");
            }

        } catch (RuntimeException | ConnectionException e) {
            System.err.println("Error with Ethernet connection: " + e.getMessage());
        }

        // The UniversalConnectionManager's shutdown hook will handle closing connections on JVM exit.
        // You can also manually call connectionManager.shutdownAllConnections(); if needed.
    }
}