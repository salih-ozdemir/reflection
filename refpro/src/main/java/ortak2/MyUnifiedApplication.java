package ortak2;

import io.grpc.ClientInterceptor;
import java.util.Arrays;
import java.util.List;

// Your common library's interfaces and classes
// import com.yourlibrary.common.connection.ManagedConnection;
// import com.yourlibrary.common.connection.UniversalConnectionManager;
// import com.yourlibrary.common.connection.GrpcManagedConnection;
// import com.yourlibrary.common.connection.EthernetManagedConnection;
// import com.yourlibrary.common.connection.ConnectionException;
// import com.yourlibrary.common.connection.GrpcStubFactory; // <-- This is key

// Your gRPC service generated classes (these are still needed in the consuming app)
import com.example.MyServiceGrpc;
import com.example.MyProto;
import io.grpc.StatusRuntimeException; // Only for specific exception handling

public class MyUnifiedApplication {
    private static final UniversalConnectionManager connectionManager = new UniversalConnectionManager();

    public static void main(String[] args) {
        // Assume you have a list of common gRPC interceptors
        // These can be defined in your common library or passed in from the application
        List<ClientInterceptor> commonGrpcInterceptors = Arrays.asList(new CustomRetryInterceptor());

        // --- Managing gRPC Connections ---
        try {
            // Get or create a gRPC connection instance from the manager
            // The factory provides the specific GrpcManagedConnection implementation
            GrpcManagedConnection grpcConn = connectionManager.getOrCreateConnection(
                    "localhost:50051",
                    id -> new GrpcManagedConnection(id, commonGrpcInterceptors)
            );

            // Now, get the gRPC stub using the generic getStub method on GrpcManagedConnection
            // The lambda here tells it *how* to create the MyServiceBlockingStub
            MyServiceGrpc.MyServiceBlockingStub grpcClient = grpcConn.getStub(MyServiceGrpc.newBlockingStub::new);

            // Make an RPC call
            System.out.println("Making gRPC call...");
            MyProto.MyResponse response = grpcClient.myMethod(MyProto.MyRequest.newBuilder().setName("Unified").build());
            System.out.println("gRPC Response: " + response.getMessage());

        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        } catch (RuntimeException | ConnectionException e) {
            System.err.println("Error with gRPC connection/call: " + e.getMessage());
        }

        // --- Managing Ethernet Connections (Remains the same as it was already generic) ---
        try {
            EthernetManagedConnection ethConn = connectionManager.getOrCreateConnection(
                    "ethernet-device:12345",
                    id -> {
                        String[] parts = id.split(":");
                        return new EthernetManagedConnection(parts[0], Integer.parseInt(parts[1]));
                    }
            );

            if (ethConn.isConnected()) {
                System.out.println("Ethernet is connected. Socket local port: " + ethConn.getSocket().getLocalPort());
                // Further Ethernet communication logic
            } else {
                System.out.println("Ethernet connection is not active.");
            }

        } catch (RuntimeException | ConnectionException e) {
            System.err.println("Error with Ethernet connection: " + e.getMessage());
        }
    }
}