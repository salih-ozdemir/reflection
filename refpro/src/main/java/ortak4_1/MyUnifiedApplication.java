package ortak4_1;

import com.yourcompany.common.connection.ConnectionException;
import com.yourcompany.common.connection.EthernetManagedConnection;
import com.yourcompany.common.connection.GrpcManagedConnection;
import com.yourcompany.common.connection.UniversalConnectionManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ClientInterceptor;
import io.grpc.StatusRuntimeException;
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

    private static final List<ClientInterceptor> GLOBAL_GRPC_INTERCEPTORS = Arrays.asList(new CustomRetryInterceptor());

    // UniversalConnectionManager'ı global interceptor'larla başlat
    private static final UniversalConnectionManager connectionManager = new UniversalConnectionManager(GLOBAL_GRPC_INTERCEPTORS);
    private static final ExecutorService grpcCallbackExecutor = Executors.newFixedThreadPool(5);


    public static void main(String[] args) throws InterruptedException {
        // --- Uygulama Başlangıcında Asenkron Servis Proxylerini Kaydet ---
        // Bu adım, uygulamanın gRPC servislerini nasıl oluşturacağını kütüphaneye bildirir.
        // MyServiceGrpcAsyncClientImpl'in bir GrpcManagedConnection alarak MyServiceAsyncClient olarak nasıl oluşturulacağını belirtiyoruz.
        connectionManager.registerAsyncServiceProxy(
                "MyService",
                MyServiceGrpcAsyncClientImpl::new // Lambda, GrpcManagedConnection'ı alıp MyServiceGrpcAsyncClientImpl döndürüyor
        );

        // --- gRPC Bağlantı ve Servis Çağrısı ---
        try {
            // Asenkron servis istemcisini UniversalConnectionManager'dan talep et
            // Uygulama sadece "MyService" isimli servisin MyServiceAsyncClient türünü istediğini belirtir.
            // gRPC'ye özgü tüm stub oluşturma detayları MyServiceGrpcAsyncClientImpl içinde kapsüllendi.
            MyServiceAsyncClient myServiceClient = connectionManager.getAsyncServiceClient(
                    "MyService",
                    "localhost:50051", // gRPC servis hedef adresi
                    MyServiceAsyncClient.class
            );

            logger.info("Asenkron gRPC çağrısı başlatılıyor (Soyutlanmış MyServiceAsyncClient)...");
            ListenableFuture<String> futureResponse = myServiceClient.myMethod("Asynchronous Abstracted Client Request");

            Futures.addCallback(futureResponse, new FutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    logger.info("Asenkron gRPC (Soyutlanmış) yanıtı: {}", result);
                }

                @Override
                public void onFailure(Throwable t) {
                    logger.error("Asenkron gRPC (Soyutlanmış) çağrısı başarısız oldu: {}", t.getMessage(), t);
                }
            }, grpcCallbackExecutor);

            // Başka bir servis çağrısı (tekrar eden)
            ListenableFuture<String> anotherFutureResponse = myServiceClient.myMethod("Another Async Request");
            Futures.addCallback(anotherFutureResponse, new FutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    logger.info("İkinci Asenkron gRPC (Soyutlanmış) yanıtı: {}", result);
                }
                @Override
                public void onFailure(Throwable t) {
                    logger.error("İkinci Asenkron gRPC (Soyutlanmış) çağrısı başarısız oldu: {}", t.getMessage(), t);
                }
            }, grpcCallbackExecutor);


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

        // Asenkron işlemlerin tamamlanması için bir süre bekle
        TimeUnit.SECONDS.sleep(10);

        // Uygulama kapanırken executor'ları düzgünce kapat
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