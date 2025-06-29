package grpc;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import com.example.grpc.MyServiceProto.MyRequest;
// import com.example.grpc.MyServiceProto.MyResponse;

@Service
public class MyApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MyApplicationService.class);

    private final GrpcService grpcService;

    public MyApplicationService(GrpcService grpcService) {
        this.grpcService = grpcService;
    }

    public void processDataAndSendToGrpc(String data) {
        if (!grpcService.isConnected()) {
            log.warn("gRPC service is not connected. Cannot send message: {}", data);
            // Optionally, you might queue this message, or throw an exception
            return;
        }

        MyRequest request = MyRequest.newBuilder().setMessage(data).build();
        log.info("Sending message to gRPC: {}", data);

        grpcService.sendAsyncMessage(request, new MyResponseStreamObserver("data-processor-call") {
            @Override
            public void onNext(MyResponse response) {
                super.onNext(response); // Call parent for logging
                // Custom logic for this specific response
                log.info("Application service received specific response: {}", response.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                super.onError(t); // Call parent for logging
                // Custom error handling for this specific call
                log.error("Application service encountered error for call: {}", t.getMessage());
            }
        });
    }

    public String getGrpcConnectionStatus() {
        return grpcService.getConnectionState().toString();
    }
}