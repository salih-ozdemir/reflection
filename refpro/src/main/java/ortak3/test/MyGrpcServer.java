package ortak3.test;

import com.yourcompany.myapp.grpc.MyServiceGrpc;
import com.yourcompany.myapp.grpc.MyServiceProto;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyGrpcServer {
    private static final Logger logger = LoggerFactory.getLogger(MyGrpcServer.class);
    private Server server;

    public void start() throws IOException {
        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(new MyServiceImpl())
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
                MyGrpcServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    static class MyServiceImpl extends MyServiceGrpc.MyServiceImplBase {
        @Override
        public void myMethod(MyServiceProto.MyRequest request, StreamObserver<MyServiceProto.MyResponse> responseObserver) {
            logger.info("Received gRPC request: " + request.getName());
            MyServiceProto.MyResponse reply = MyServiceProto.MyResponse.newBuilder()
                    .setMessage("Hello " + request.getName() + " from gRPC Server!")
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final MyGrpcServer server = new MyGrpcServer();
        server.start();
        server.blockUntilShutdown();
    }
}
