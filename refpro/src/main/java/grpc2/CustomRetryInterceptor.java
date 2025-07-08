package grpc2;

// Custom Retry Interceptor Example (Simplified)
public class CustomRetryInterceptor implements ClientInterceptor {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            private int retries = 0;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (status.getCode() == Status.Code.UNAVAILABLE || status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                            if (retries < MAX_RETRIES) {
                                retries++;
                                System.out.println("Connection lost for " + method.getFullMethodName() + ". Retrying (" + retries + "/" + MAX_RETRIES + ")");
                                try {
                                    Thread.sleep(RETRY_DELAY_MS); // Simple delay
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                // Re-attempt the call
                                next.newCall(method, callOptions).start(this, headers);
                                return; // Don't propagate the error yet
                            }
                        }
                        super.onClose(status, trailers); // Propagate status
                    }
                }, headers);
            }
        };
    }
}