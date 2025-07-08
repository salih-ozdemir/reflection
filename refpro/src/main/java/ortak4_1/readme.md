grpc async olacak şekilde genellesek


Süper bir fikir! Mevcut gRPC istemci yönetimini tamamen asenkron hale getirerek ve genel bir servis soyutlaması ekleyerek kütüphanemizi bir üst seviyeye taşıyabiliriz. Bu, uygulamanızın gRPC'nin asenkron yapısını doğrudan kullanmasını zorunlu kılar ve iş parçacığı yönetimi gibi detayları kütüphane içinde daha iyi kapsüller.

Amacımız, uygulamanın sadece bir servisi çağırmak istediğini belirtmesi ve kütüphanenin, o servisin gRPC mi, REST mi olduğunu bilmeden, çağrıyı asenkron olarak halletmesidir.

Yeni Yaklaşım: Tamamen Asenkron ve Protokol-Agnostik Servis İstemcisi
Bu soyutlamayı başarmak için kilit noktalar şunlar:

AsyncServiceClient: Tüm servis istemcilerinin (gRPC veya Ethernet) uygulayacağı asenkron bir arayüz. Bu, bir Future veya benzeri bir asenkron sonuç döndürecektir.

UniversalConnectionManager içindeki iyileştirme: Artık sadece bağlantıları değil, bu bağlantılar üzerinden türetilen soyut asenkron servis istemcilerini de yönetecek.

gRPC Tarafında Kapsülleme: GrpcManagedConnection ve yeni bir GrpcAsyncServiceFactory veya GrpcServiceProxy sınıfı, gRPC AsyncStub'larını ve StreamObserver tabanlı çağrıları içeride yönetecek. Uygulama, ListenableFuture döndüren basit metotlar çağıracak.

Proje Yapısı ve Bağımlılıklar
pom.xml ve genel proje yapısı önceki örneklerle aynı kalabilir. Gerekli gRPC ve Guava bağımlılıklarının olduğundan emin olun.

Ortak Bağlantı Kütüphanesi (com.yourcompany.common.connection)
AsyncServiceClient.java (Yeni Temel Arayüz)
Bu, tüm asenkron servis istemcilerinizin uygulayacağı genel arayüzdür. Örneğin, bir callService metodu gibi bir şey tanımlanabilir. Ancak, her servisin farklı metotları olduğundan, genellikle bu arayüz boş kalır ve sadece bir işaretleyici görevi görür.

Java

package com.yourcompany.common.connection;

/**
* Marker interface for all asynchronous service clients managed by the UniversalConnectionManager.
* This ensures that clients retrieved from the manager are expected to be asynchronous.
  */
  public interface AsyncServiceClient {
  // Specific service methods will be defined in concrete client interfaces/classes.
  }
  GrpcManagedConnection.java (Değişiklik Yok)
  Bu sınıf, ManagedChannel'ı yönetmeye devam edecek. getStub ve getServiceClient metodları hala kullanışlı olabilir, ancak uygulamanın doğrudan bunlarla etkileşimi azalacak.

Java

// GrpcManagedConnection.java önceki haliyle aynı kalabilir.
// Çünkü bu sınıf gRPC kanalını ve temel stub oluşturma mantığını sağlar.
// Asıl soyutlama, UniversalConnectionManager ve yeni eklenecek "Proxy" katmanında olacak.
UniversalConnectionManager.java (Değişiklikler)
UniversalConnectionManager artık sadece ManagedConnection'ları değil, aynı zamanda bu bağlantılar üzerinden türetilebilecek soyut servis istemcilerini de yönetecek. En önemlisi, asenkron servis proxy'lerini kaydedeceğiz.

Java

package com.yourcompany.common.connection;

import io.grpc.ClientInterceptor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UniversalConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(UniversalConnectionManager.class);

    private final Map<String, ManagedConnection> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        t.setName("connection-reconnect-scheduler");
        return t;
    });

    // Yeni: Servis proxy'lerini (soyut istemcileri) saklamak için
    // Key: Servis adı, Value: Servis proxy'sinin fabrika metodu
    private final Map<String, Function<GrpcManagedConnection, ? extends AsyncServiceClient>> grpcServiceProxies = new ConcurrentHashMap<>();
    
    private final List<ClientInterceptor> globalGrpcInterceptors;

    public UniversalConnectionManager(List<ClientInterceptor> globalGrpcInterceptors) {
        this.globalGrpcInterceptors = globalGrpcInterceptors;

        reconnectionScheduler.scheduleWithFixedDelay(() -> {
            activeConnections.forEach((id, conn) -> {
                if (!conn.isConnected()) {
                    logger.warn("Connection '{}' detected as disconnected. Attempting to reconnect...", id);
                    try {
                        conn.connect();
                        if (conn.isConnected()) {
                            logger.info("Successfully reconnected to '{}'.", id);
                        }
                    } catch (ConnectionException e) {
                        logger.error("Failed to reconnect '{}': {}", id, e.getMessage());
                    }
                }
            });
        }, 5, 10, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAllConnections));
    }

    // --- Bağlantı Yönetimi Metotları (Öncekiyle aynı) ---
    @FunctionalInterface
    public interface ConnectionFactory<T extends ManagedConnection> {
        T create(String identifier);
    }

    public <T extends ManagedConnection> T getOrCreateConnection(String identifier, ConnectionFactory<T> factory) throws ConnectionException {
        return (T) activeConnections.computeIfAbsent(identifier, id -> {
            logger.info("Creating new connection for: {}", id);
            T newConnection = factory.create(id);
            try {
                newConnection.connect();
            } catch (ConnectionException e) {
                logger.error("Initial connection failed for '{}': {}", id, e.getMessage());
                throw new RuntimeException(e);
            }
            return newConnection;
        });
    }

    public <T extends ManagedConnection> T getConnection(String identifier, Class<T> connectionType) {
        ManagedConnection conn = activeConnections.get(identifier);
        if (conn != null && connectionType.isInstance(conn)) {
            return connectionType.cast(conn);
        }
        return null;
    }

    // --- Yeni: Asenkron Servis Proxy Kayıt ve Alma Metotları ---

    /**
     * Registers an asynchronous service proxy factory.
     * This factory knows how to create a specific AsyncServiceClient (e.g., MyServiceAsyncClient)
     * using a GrpcManagedConnection.
     *
     * @param serviceName The logical name of the service (e.g., "MyService").
     * @param proxyFactory A function that takes a GrpcManagedConnection and returns an instance of AsyncServiceClient.
     * @param <T> The type of the AsyncServiceClient.
     */
    public <T extends AsyncServiceClient> void registerAsyncServiceProxy(
            String serviceName, Function<GrpcManagedConnection, T> proxyFactory) {
        grpcServiceProxies.put(serviceName, proxyFactory);
        logger.info("Registered async service proxy for: {}", serviceName);
    }

    /**
     * Retrieves a specific asynchronous service client (proxy) instance.
     * The application only needs to provide the service name and the target address.
     * The actual gRPC stub creation is handled internally.
     *
     * @param serviceName The logical name of the gRPC service (e.g., "MyService").
     * @param grpcTargetAddress The address of the gRPC server (e.g., "localhost:50051").
     * @param serviceClientClass The class of the desired asynchronous service client (e.g., MyServiceAsyncClient.class).
     * @param <T> The type of the AsyncServiceClient.
     * @return An instance of the requested asynchronous service client.
     * @throws IllegalArgumentException If the service or its proxy factory is not registered.
     * @throws ConnectionException If the underlying gRPC connection cannot be established.
     * @throws IllegalStateException If the gRPC channel is not connected.
     */
    @SuppressWarnings("unchecked")
    public <T extends AsyncServiceClient> T getAsyncServiceClient(
            String serviceName, String grpcTargetAddress, Class<T> serviceClientClass)
            throws ConnectionException {

        // 1. gRPC bağlantısını al veya oluştur
        GrpcManagedConnection grpcConn = getOrCreateConnection(
            grpcTargetAddress,
            id -> new GrpcManagedConnection(id, globalGrpcInterceptors)
        );

        // 2. Servis proxy fabrika metodunu al
        Function<GrpcManagedConnection, ? extends AsyncServiceClient> proxyFactory = grpcServiceProxies.get(serviceName);
        if (proxyFactory == null) {
            throw new IllegalArgumentException("No async service proxy registered for: " + serviceName);
        }

        // 3. Fabrika ile asenkron servis istemcisini oluştur
        AsyncServiceClient client = proxyFactory.apply(grpcConn);
        
        if (!serviceClientClass.isInstance(client)) {
             throw new ClassCastException("Expected client of type " + serviceClientClass.getName() + " but got " + client.getClass().getName());
        }
        return (T) client;
    }

    // --- Genel Kapatma Metodu (Öncekiyle aynı) ---
    public void shutdownAllConnections() {
        logger.info("Shutting down all managed connections...");
        reconnectionScheduler.shutdownNow();
        activeConnections.values().forEach(ManagedConnection::disconnect);
        activeConnections.clear();
        logger.info("All managed connections shut down.");
    }
}
Uygulama Kodu (com.yourcompany.myapp)
MyServiceAsyncClient.java (Yeni: Uygulama Tarafı Asenkron İstemci Arayüzü ve İmplementasyonu)
Bu, uygulamanızın kullanacağı soyut asenkron servis istemcisidir. gRPC detayları burada kapsüllenecek.

Java

package com.yourcompany.myapp;

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
  MyUnifiedApplication.java (Final Durum)
  Java

package com.yourcompany.myapp;

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
Bu Soyutlamanın Faydaları
Tam Protokol Bağımsızlığı (Uygulama Seviyesinde): Uygulama katmanı artık gRPC'nin varlığından bile haberdar değil. Sadece MyServiceAsyncClient adında bir asenkron servis istemcisi talep ediyor ve onun myMethod gibi işlevsel metotlarını çağırıyor.

Kapsamlı Asenkron Yaklaşım: Tüm servis çağrıları ListenableFuture döndürerek uygulamanın asenkron bir modelle çalışmasını zorunlu kılıyor. Bu, bloklama yapmadan IO operasyonları gerçekleştirmenizi sağlar.

Kütüphane İçinde gRPC Detayları: MyServiceGrpcAsyncClientImpl sınıfı, gRPC'nin MyServiceGrpc.newFutureStub::new gibi detaylarını ve MyServiceProto gibi protobuf mesajlarını tamamen kendi içine kapsüller. Uygulama kodunda bu importlara ve çağrılara gerek kalmaz.

Esneklik: Gelecekte MyService'i gRPC'den başka bir protokole (örneğin REST) taşımak istediğinizde, sadece MyServiceGrpcAsyncClientImpl yerine MyServiceRestAsyncClientImpl gibi yeni bir implementasyon yazarsınız ve UniversalConnectionManager.registerAsyncServiceProxy çağrısını güncellersiniz. Uygulama kodu hiç değişmez.

Test Edilebilirlik: MyServiceAsyncClient arayüzü sayesinde, uygulamanızın servis katmanını kolayca mock'layabilir ve entegrasyon testleri için gerçek gRPC veya Ethernet bağlantısına ihtiyaç duymadan test edebilirsiniz.

Bu, mikroservis mimarilerinde güçlü bir tasarım prensibidir. Uygulamanızın servis tüketimi, alttaki iletişim protokolünden tamamen izole edilir.