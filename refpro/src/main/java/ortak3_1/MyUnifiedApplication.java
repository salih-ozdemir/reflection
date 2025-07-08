package ortak3_1;


import com.yourcompany.common.connection.ConnectionException;
import com.yourcompany.common.connection.EthernetManagedConnection;
import com.yourcompany.common.connection.GrpcManagedConnection;
import com.yourcompany.common.connection.UniversalConnectionManager;
import com.yourcompany.myapp.grpc.MyServiceGrpc;
import com.yourcompany.myapp.grpc.MyServiceProto;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ClientInterceptor;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MyUnifiedApplication {
    private static final Logger logger = LoggerFactory.getLogger(MyUnifiedApplication.class);
    private static final UniversalConnectionManager connectionManager = new UniversalConnectionManager();
    private static final ExecutorService grpcCallbackExecutor = Executors.newFixedThreadPool(5);


    public static void main(String[] args) throws InterruptedException {
        List<ClientInterceptor> commonGrpcInterceptors = Arrays.asList(new CustomRetryInterceptor());

        // --- gRPC Bağlantı Yönetimi ---
        try {
            // gRPC bağlantısını al veya oluştur
            GrpcManagedConnection grpcConn = connectionManager.getOrCreateConnection(
                    "localhost:50051", // Hedef gRPC servis adresi
                    id -> new GrpcManagedConnection(id, commonGrpcInterceptors)
            );

            // Asenkron gRPC çağrısı (FutureStub kullanarak)
            // ARTIK MyServiceGrpc.newFutureStub::new direkt olarak burada çağrılmıyor!
            // Bu lambda, GrpcManagedConnection içindeki getServiceClient metoduna taşındı.
            MyServiceGrpc.MyServiceFutureStub futureClient = grpcConn.getServiceClient(
                    MyServiceGrpc.MyServiceFutureStub.class, // İstenen stub'ın tipi
                    MyServiceGrpc.newFutureStub::new        // Stub'ı nasıl oluşturacağını söyleyen fabrika metodu
            );

            logger.info("Asenkron gRPC çağrısı (FutureStub) başlatılıyor...");
            ListenableFuture<MyServiceProto.MyResponse> futureResponse = futureClient.myMethod(
                    MyServiceProto.MyRequest.newBuilder().setName("Async gRPC Request").build()
            );

            Futures.addCallback(futureResponse, new FutureCallback<MyServiceProto.MyResponse>() {
                @Override
                public void onSuccess(MyServiceProto.MyResponse result) {
                    logger.info("Asenkron gRPC (FutureStub) yanıtı: {}", result.getMessage());
                }

                @Override
                public void onFailure(Throwable t) {
                    logger.error("Asenkron gRPC (FutureStub) çağrısı başarısız oldu: {}", t.getMessage(), t);
                }
            }, grpcCallbackExecutor);

            // Asenkron gRPC çağrısı (AsyncStub/StreamObserver kullanarak)
            MyServiceGrpc.MyServiceStub asyncClient = grpcConn.getServiceClient(
                    MyServiceGrpc.MyServiceStub.class, // İstenen stub'ın tipi
                    MyServiceGrpc.newStub::new       // Stub'ı nasıl oluşturacağını söyleyen fabrika metodu
            );

            logger.info("Asenkron gRPC çağrısı (AsyncStub/StreamObserver) başlatılıyor...");

            StreamObserver<MyServiceProto.MyResponse> responseObserver = new StreamObserver<MyServiceProto.MyResponse>() {
                @Override
                public void onNext(MyServiceProto.MyResponse response) {
                    logger.info("Asenkron gRPC (StreamObserver) yanıtı: {}", response.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    logger.error("Asenkron gRPC (StreamObserver) çağrısı başarısız oldu: {}", t.getMessage(), t);
                }

                @Override
                public void onCompleted() {
                    logger.info("Asenkron gRPC (StreamObserver) çağrısı tamamlandı.");
                }
            };

            asyncClient.myMethod(MyServiceProto.MyRequest.newBuilder().setName("StreamObserver gRPC Request").build(), responseObserver);

        } catch (RuntimeException | ConnectionException e) {
            logger.error("gRPC bağlantısı veya çağrısı sırasında hata oluştu: {}", e.getMessage(), e);
        }

        // --- Ethernet Bağlantı Yönetimi (Değişiklik Yok) ---
        try {
            EthernetManagedConnection ethConn = connectionManager.getOrCreateConnection(
                    "localhost:12345", // Hedef Ethernet cihaz adresi (host:port)
                    id -> {
                        String[] parts = id.split(":");
                        return new EthernetManagedConnection(parts[0], Integer.parseInt(parts[1]));
                    }
            );

            if (ethConn.isConnected()) {
                logger.info("Ethernet bağlantısı etkin. Socket lokal portu: {}", ethConn.getSocket().getLocalPort());
                try (OutputStream os = ethConn.getSocket().getOutputStream()) {
                    String dataToSend = "Hello Ethernet Device!";
                    os.write(dataToSend.getBytes(StandardCharsets.UTF_8));
                    logger.info("Ethernet üzerinden veri gönderildi: '{}'", dataToSend);
                } catch (Exception e) {
                    logger.error("Ethernet üzerinden veri gönderilirken hata: {}", e.getMessage(), e);
                }
            } else {
                logger.warn("Ethernet bağlantısı aktif değil, veri gönderilemedi.");
            }

        } catch (RuntimeException | ConnectionException e) {
            logger.error("Ethernet bağlantısı sırasında hata oluştu: {}", e.getMessage(), e);
        }

        TimeUnit.SECONDS.sleep(10);

        grpcCallbackExecutor.shutdown();
        try {
            if (!grpcCallbackExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("gRPC callback executor didn't terminate in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}