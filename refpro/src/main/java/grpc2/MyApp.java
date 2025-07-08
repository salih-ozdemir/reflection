package grpc2;

// ---

// How other Microservices Would Use Your Library

// In your application's main class or a configuration class:
public class MyApp {
    private static final GrpcChannelManager grpcChannelManager = new GrpcChannelManager();

    public static void main(String[] args) {
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(grpcChannelManager::shutdownAllChannels));

        // ... your application logic ...

        // Example usage:
        MyServiceGrpc.MyServiceBlockingStub myServiceClient = grpcChannelManager.getStub(
                "localhost:8080",
                MyServiceGrpc.newBlockingStub::new
        );

        // Make an RPC call
        try {
            MyProto.MyResponse response = myServiceClient.myMethod(MyProto.MyRequest.newBuilder().setName("World").build());
            System.out.println("Response: " + response.getMessage());
        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        }
    }
}