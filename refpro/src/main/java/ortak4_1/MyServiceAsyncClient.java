package ortak4_1;


import com.google.common.util.concurrent.ListenableFuture;
import com.yourcompany.common.connection.AsyncServiceClient;
import com.yourcompany.common.connection.GrpcManagedConnection;
import com.yourcompany.myapp.grpc.MyServiceGrpc;
import com.yourcompany.myapp.grpc.MyServiceProto;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An asynchronous client for MyService, abstracting gRPC specific details.
 */
public interface MyServiceAsyncClient extends AsyncServiceClient {
    ListenableFuture<String> myMethod(String name);
    // Add other async methods for MyService here
}

/**
 * Implementation of MyServiceAsyncClient that uses gRPC's AsyncStub.
 */
class MyServiceGrpcAsyncClientImpl implements MyServiceAsyncClient {
    private static final Logger logger = LoggerFactory.getLogger(MyServiceGrpcAsyncClientImpl.class);

    private final MyServiceGrpc.MyServiceStub asyncStub;
    private final MyServiceGrpc.MyServiceFutureStub futureStub;

    public MyServiceGrpcAsyncClientImpl(GrpcManagedConnection grpcConnection) {
        // Here, we create the gRPC specific stubs using the provided connection.
        // The application calling this client doesn't need to know about these stubs.
        this.asyncStub = grpcConnection.getStub(MyServiceGrpc.newStub::new);
        this.futureStub = grpcConnection.getStub(MyServiceGrpc.newFutureStub::new);
        logger.debug("MyServiceGrpcAsyncClientImpl created with connection: {}", grpcConnection.getIdentifier());
    }

    @Override
    public ListenableFuture<String> myMethod(String name) {
        MyServiceProto.MyRequest request = MyServiceProto.MyRequest.newBuilder().setName(name).build();
        logger.debug("Calling myMethod asynchronously with name: {}", name);

        // Map the gRPC ListenableFuture<MyResponse> to ListenableFuture<String>
        ListenableFuture<MyServiceProto.MyResponse> grpcFuture = futureStub.myMethod(request);

        // Transform the future result to the desired type for the consumer
        return com.google.common.util.concurrent.Futures.transform(
                grpcFuture,
                MyServiceProto.MyResponse::getMessage, // Extract the string message
                com.google.common.util.concurrent.MoreExecutors.directExecutor() // Use appropriate executor
        );
    }

    // Example of how to handle streaming RPCs internally (if MyService had them)
    // public StreamObserver<String> clientStreamingMethod(StreamObserver<String> responseObserver) {
    //     return new StreamObserver<MyServiceProto.MyRequest>() {
    //         @Override
    //         public void onNext(MyServiceProto.MyRequest request) { /* handle request */ }
    //         @Override
    //         public void onError(Throwable t) { /* handle error */ }
    //         @Override
    //         public void onCompleted() { /* handle completion */ }
    //     };
    // }
}