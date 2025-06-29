package grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import com.example.grpc.MyServiceProto.MyResponse; // Assuming your response message

public class MyResponseStreamObserver implements StreamObserver<MyResponse> {

    private static final Logger log = LoggerFactory.getLogger(MyResponseStreamObserver.class);

    private final String callIdentifier; // Useful for logging

    public MyResponseStreamObserver(String callIdentifier) {
        this.callIdentifier = callIdentifier;
    }

    @Override
    public void onNext(MyResponse response) {
        log.info("[{}] Received gRPC response: {}", callIdentifier, response.getMessage());
        // Process your response here
    }

    @Override
    public void onError(Throwable t) {
        Status status = Status.fromThrowable(t);
        log.error("[{}] gRPC call failed with status: {} - {}", callIdentifier, status.getCode(), status.getDescription());
        if (t instanceof StatusRuntimeException) {
            StatusRuntimeException sre = (StatusRuntimeException) t;
            // You might want to trigger specific actions based on status codes
            if (sre.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                log.warn("[{}] Server is unavailable, consider triggering a reconnect logic if not handled by service.", callIdentifier);
            }
        }
        // Handle the error appropriately, e.g., notify upstream, log, retry specific operations.
    }

    @Override
    public void onCompleted() {
        log.info("[{}] gRPC call completed.", callIdentifier);
    }
}