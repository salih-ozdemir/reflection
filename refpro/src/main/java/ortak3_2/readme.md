Harika bir hedef! MyServiceGrpc.newFutureStub::new gibi gRPC'ye özgü stub oluşturma mantığını tamamen ortak kütüphanenizin içine almak, uygulama katmanını gRPC detaylarından daha da soyutlar. Bu, uygulamanızın sadece bir "servis" talep etmesini sağlar, o servisin gRPC mi, REST mi, yoksa başka bir protokol mü olduğunu bilmesine gerek kalmaz.

Bu seviyede bir soyutlama için Service Locator veya Dependency Injection benzeri bir yapıya ihtiyacımız var. Kütüphaneniz, talep edildiğinde belirli bir servisin istemcisini (stub'ını) sağlayabilen bir kayda sahip olmalı.

Yeni Yaklaşım: Soyut Servis İstemcisi ve Kayıt Mekanizması
Bu soyutlamayı başarmak için şu adımları izleyeceğiz:

Ortak Kütüphane Tarafında:

AbstractServiceClient: Tüm servis istemcilerinin (gRPC, Ethernet fark etmeksizin) uygulayacağı marker (işaretleyici) bir arayüz.

GrpcServiceDescriptor: Bir gRPC servisini tanımlayan ve o servisin farklı stub türlerini (Blocking, Future, Async) nasıl oluşturacağını bilen bir yapı. Bu, MyServiceGrpc.newFutureStub::new gibi lambda ifadelerini kütüphane içinde tutmamızı sağlar.

ServiceRegistry: Uygulamanın talep edeceği tüm servis istemcilerini (veya onların oluşturucularını) barındıran merkezi bir kayıt defteri.

UniversalConnectionManager içindeki iyileştirme: Artık sadece bağlantıları değil, bu bağlantılar üzerinden türetilen soyut servis istemcilerini de yönetebiliriz.

Uygulama Tarafında (MyUnifiedApplication):

Uygulama, doğrudan MyServiceGrpc sınıflarını içe aktarmayacak (veya en azından stub oluşturma yöntemlerini çağırmayacak).

Uygulama, ServiceRegistry'den AbstractServiceClient tipinde bir servis istemcisi talep edecek.

Ortak Bağlantı Kütüphanesi (com.yourcompany.common.connection)
AbstractServiceClient.java (Yeni)
Java

package com.yourcompany.common.connection;

/**
* Marker interface for all service clients managed by the UniversalConnectionManager.
* This allows the manager to return a generic client type without knowing the specific protocol.
  */
  public interface AbstractServiceClient {
  // This interface might be empty or contain common methods like 'healthCheck()' if applicable
  }
  GrpcServiceDescriptor.java (Yeni)
  Bu sınıf, belirli bir gRPC servisi için FutureStub, AsyncStub, BlockingStub gibi farklı istemci tiplerini nasıl oluşturacağını bilir.

Java

package com.yourcompany.common.connection;

import com.google.common.base.Preconditions;
import io.grpc.Channel;
import io.grpc.stub.AbstractStub;
import java.util.function.Function;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
* Describes how to create different types of stubs for a specific gRPC service.
* This encapsulates the gRPC-specific stub creation logic.
  */
  public class GrpcServiceDescriptor {
  private final String serviceName; // E.g., "MyService"
  private final Map<Class<? extends AbstractStub<?>>, Function<Channel, ? extends AbstractStub<?>>> stubFactories;

  private GrpcServiceDescriptor(String serviceName) {
  this.serviceName = Preconditions.checkNotNull(serviceName, "Service name cannot be null");
  this.stubFactories = new ConcurrentHashMap<>();
  }

  public static GrpcServiceDescriptor of(String serviceName) {
  return new GrpcServiceDescriptor(serviceName);
  }

  /**
    * Registers a factory for a specific stub type.
    * @param stubClass The class of the stub (e.g., MyServiceGrpc.MyServiceFutureStub.class)
    * @param factory The function to create the stub from a gRPC Channel (e.g., MyServiceGrpc.newFutureStub::new)
    * @param <T> The type of the stub
    * @return This GrpcServiceDescriptor instance for chaining
      */
      public <T extends AbstractStub<T>> GrpcServiceDescriptor registerStubFactory(
      Class<T> stubClass, Function<Channel, T> factory) {
      Preconditions.checkNotNull(stubClass, "Stub class cannot be null");
      Preconditions.checkNotNull(factory, "Factory cannot be null");
      stubFactories.put(stubClass, factory);
      return this;
      }

  /**
    * Creates a stub of the specified type using the registered factory.
    * @param stubClass The class of the stub to create
    * @param channel The gRPC Channel to use
    * @param <T> The type of the stub
    * @return The created stub
    * @throws IllegalArgumentException If no factory is registered for the given stub class
      */
      @SuppressWarnings("unchecked")
      public <T extends AbstractStub<T>> T createStub(Class<T> stubClass, Channel channel) {
      Function<Channel, ? extends AbstractStub<?>> factory = stubFactories.get(stubClass);
      if (factory == null) {
      throw new IllegalArgumentException("No factory registered for stub class: " + stubClass.getName());
      }
      return (T) factory.apply(channel);
      }

  public String getServiceName() {
  return serviceName;
  }
  }
  UniversalConnectionManager.java (Değişiklikler)
  UniversalConnectionManager artık sadece ManagedConnection'ları değil, aynı zamanda bu bağlantılar üzerinden türetilebilecek gRPC servis tanımlayıcılarını da kaydedecek.

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

    // Mevcut bağlantı yönetimi
    private final Map<String, ManagedConnection> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        t.setName("connection-reconnect-scheduler");
        return t;
    });

    // Yeni: gRPC servis tanımlayıcılarını saklamak için
    private final Map<String, GrpcServiceDescriptor> grpcServiceDescriptors = new ConcurrentHashMap<>();
    private final List<ClientInterceptor> globalGrpcInterceptors; // Ortak interceptor'lar

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

    // --- Yeni: gRPC Servis Kayıt ve Alma Metotları ---

    /**
     * Registers a GrpcServiceDescriptor for a specific gRPC service.
     * This allows the manager to create specific stubs later without direct application involvement.
     * @param descriptor The GrpcServiceDescriptor for the service.
     */
    public void registerGrpcService(GrpcServiceDescriptor descriptor) {
        grpcServiceDescriptors.put(descriptor.getServiceName(), descriptor);
        logger.info("Registered gRPC service descriptor for: {}", descriptor.getServiceName());
    }

    /**
     * Retrieves a specific gRPC service client (stub) instance.
     * The application only needs to provide the service name and the desired stub type.
     *
     * @param serviceName The logical name of the gRPC service (e.g., "MyService").
     * @param grpcTargetAddress The address of the gRPC server (e.g., "localhost:50051").
     * @param stubClass The class of the desired gRPC stub (e.g., MyServiceGrpc.MyServiceFutureStub.class).
     * @param <T> The type of the gRPC stub.
     * @return An instance of the requested gRPC stub.
     * @throws IllegalArgumentException If the service or stub type is not registered.
     * @throws ConnectionException If the underlying gRPC connection cannot be established.
     * @throws IllegalStateException If the gRPC channel is not connected.
     */
    public <T extends AbstractStub<T>> T getGrpcServiceClient(
            String serviceName, String grpcTargetAddress, Class<T> stubClass)
            throws ConnectionException {

        // 1. gRPC bağlantısını al veya oluştur
        GrpcManagedConnection grpcConn = getOrCreateConnection(
            grpcTargetAddress,
            id -> new GrpcManagedConnection(id, globalGrpcInterceptors)
        );

        // 2. Servis açıklayıcısını al
        GrpcServiceDescriptor descriptor = grpcServiceDescriptors.get(serviceName);
        if (descriptor == null) {
            throw new IllegalArgumentException("No gRPC service descriptor registered for: " + serviceName);
        }

        // 3. Kanal üzerinden istenen stub'ı oluştur
        return descriptor.createStub(stubClass, grpcConn.getChannel());
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
MyUnifiedApplication.java (Değişiklikler)
Uygulamanın artık MyServiceGrpc.newFutureStub::new gibi ifadeleri çağırmasına gerek kalmadı. Startup sırasında servisi UniversalConnectionManager'a kaydettikten sonra, doğrudan getGrpcServiceClient metodunu kullanarak istemciyi talep edecek.

Java

package com.yourcompany.myapp;

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
Bu Soyutlamanın Faydaları
Daha Güçlü Dekuplaj: MyUnifiedApplication artık MyServiceGrpc.newFutureStub::new gibi gRPC'nin düşük seviyeli stub oluşturma API'lerini doğrudan çağırmıyor. Bunun yerine, UniversalConnectionManager'dan, kaydettiği bir servis adı ve istediği stub tipi ile bir istemci talep ediyor.

Kütüphane Tarafında Kapsülleme: GrpcServiceDescriptor ve onun registerStubFactory metotları, belirli bir gRPC servisinin farklı stub tiplerini nasıl oluşturulacağını ortak kütüphanenin içinde tanımlamamızı sağlar. Bu gRPC'ye özgü bilgiyi uygulama kodundan uzaklaştırır.

Daha Temiz Uygulama Kodu: Uygulamanın iş mantığı, bağlantı kurma ve stub oluşturma detaylarından daha da arındırılmış olur. Sadece istediği servisi "MyService" olarak adlandırır ve gerisini kütüphaneye bırakır.

Kolay Genişletilebilirlik: Gelecekte, başka bir RPC framework'ü (örneğin, Apache Thrift) veya REST istemcilerini de aynı ServiceRegistry mantığıyla yönetebilirsiniz. Sadece ManagedConnection arayüzünü uygulayan yeni bir sınıf ve belki de AbstractServiceClient'i uygulayan yeni istemci tipleri eklemeniz yeterli olacaktır.

Bu yapı, özellikle büyük mikroservis mimarilerinde birden fazla servisle etkileşim kuran uygulamalar için yönetilebilirliği önemli ölçüde artırır. Uygulamanızın ne tür bir "MyService" istemcisi aldığı, kütüphane ve onun konfigürasyonu tarafından belirlenir, uygulama tarafından değil.