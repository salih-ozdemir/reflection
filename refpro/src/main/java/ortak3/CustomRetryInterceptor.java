package ortak3;


import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class CustomRetryInterceptor implements ClientInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CustomRetryInterceptor.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 saniye

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            private int retries = 0;
            private long currentDelay = INITIAL_RETRY_DELAY_MS;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        // UNKNOWN (bağlantı henüz kurulamadı), UNAVAILABLE (servis kapalı/ulaşılamaz),
                        // DEADLINE_EXCEEDED (zaman aşımı) gibi durumları yakalayabiliriz.
                        if ((status.getCode() == Status.Code.UNAVAILABLE ||
                                status.getCode() == Status.Code.UNKNOWN ||
                                status.getCode() == Status.Code.DEADLINE_EXCEEDED) && retries < MAX_RETRIES) {

                            retries++;
                            logger.warn("RPC call for {} failed with status {}. Retrying {}/{} in {}ms.",
                                    method.getFullMethodName(), status.getCode(), retries, MAX_RETRIES, currentDelay);

                            try {
                                TimeUnit.MILLISECONDS.sleep(currentDelay);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                super.onClose(Status.CANCELLED.withDescription("Retry interrupted"), trailers);
                                return;
                            }

                            // Exponential backoff
                            currentDelay *= 2;

                            // Yeniden çağrıyı başlat
                            next.newCall(method, callOptions).start(this, headers);
                            return; // Hatayı henüz yukarıya iletme
                        }
                        super.onClose(status, trailers); // Diğer hataları veya retry limitini aşan hataları ilet
                    }
                }, headers);
            }
        };
    }
}