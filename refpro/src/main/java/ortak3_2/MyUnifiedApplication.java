package ortak3_2;

import com.yourcompany.common.connection.ConnectionException;
import com.yourcompany.common.connection.EthernetManagedConnection;
import com.yourcompany.common.connection.GrpcManagedConnection;
import com.yourcompany.common.connection.UniversalConnectionManager;
import com.yourcompany.common.connection.GrpcServiceDescriptor; // Yeni import
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

    // Global interceptors can be configured here (e.g., from a config file)
    private static final List<ClientInterceptor> GLOBAL_GRPC_INTERCEPTORS = Arrays.asList(new CustomRetryInterceptor());

    private static final UniversalConnectionManager connectionManager = new UniversalConnectionManager(GLOBAL_GRPC_INTERCEPTORS);
    private static final ExecutorService grpcCallbackExecutor = Executors.newFixedThreadPool(5);


    public static void main(String[] args) throws InterruptedException {
        // --- Uygulama Başlangıcında Servisleri Kaydet ---
        // Bu adım, uygulamanın gRPC servislerini nasıl oluşturacağını kütüphaneye bildirir.
        // Bu genellikle bir konfigürasyon sınıfında veya uygulama başlangıcında yapılır.
        GrpcServiceDescriptor myServiceDescriptor = GrpcServiceDescriptor.of("MyService")
                .registerStubFactory(MyServiceGrpc.MyServiceFutureStub.class, MyServiceGrpc.newFutureStub::new)
                .registerStubFactory(MyServiceGrpc.MyServiceStub.class, MyServiceGrpc.newStub::new)
                .registerStubFactory(MyServiceGrpc.MyServiceBlockingStub.class, MyServiceGrpc.newBlockingStub::new);

        connectionManager.registerGrpcService(myServiceDescriptor);

        // --- gRPC Bağlantı ve Servis Çağrısı ---
        try {
            // Servis istemcisini UniversalConnectionManager'dan talep et
            // Uygulama sadece "MyService" isimli servisin MyServiceFutureStub türünü istediğini belirtir.
            // newFutureStub::new gibi detaylar artık GrpcServiceDescriptor içinde kapsüllendi.
            MyServiceGrpc.MyServiceFutureStub futureClient = connectionManager.getGrpcServiceClient(
                    "MyService",
                    "localhost:50051", // gRPC servis hedef adresi
                    MyServiceGrpc.MyServiceFutureStub.class
            );

            logger.info("Asenkron gRPC çağrısı (FutureStub) başlatılıyor...");
            ListenableFuture<MyServiceProto.MyResponse> futureResponse = futureClient.myMethod(
                    MyServiceProto.MyRequest.newBuilder().setName("Async gRPC Request (Abstracted)").build()
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

            MyServiceGrpc.MyServiceStub asyncClient = connectionManager.getGrpcServiceClient(
                    "MyService",
                    "localhost:50051",
                    MyServiceGrpc.MyServiceStub.class
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

            asyncClient.myMethod(MyServiceProto.MyRequest.newBuilder().setName("StreamObserver gRPC Request (Abstracted)").build(), responseObserver);

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

        // Asenkron işlemlerin tamamlanması için bekle
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