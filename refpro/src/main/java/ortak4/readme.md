Java Microservice gRPC Kütüphanesi Tasarımı
gRPC tabanlı bir Java kütüphanesi tasarlarken, bağlantı yönetimi, servis keşfi ve yeniden bağlanma mekanizmalarını doğru şekilde uygulamak önemlidir. İşte istediğiniz özelliklere sahip bir kütüphane tasarımı:

Temel Yapı
java
public class GrpcClientManager {
private static final Logger logger = LoggerFactory.getLogger(GrpcClientManager.class);
private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
private final ServiceDiscovery serviceDiscovery;
private final ConnectionPoolConfig poolConfig;

    public GrpcClientManager(ServiceDiscovery serviceDiscovery, ConnectionPoolConfig poolConfig) {
        this.serviceDiscovery = serviceDiscovery;
        this.poolConfig = poolConfig;
    }
    
    // Diğer metodlar...
}
Bağlantı Yönetimi
java
public <T extends AbstractStub<T>> T getStub(Class<T> stubClass) {
String serviceName = resolveServiceName(stubClass);
ManagedChannel channel = getOrCreateChannel(serviceName);
return createStub(channel, stubClass);
}

private ManagedChannel getOrCreateChannel(String serviceName) {
return channels.computeIfAbsent(serviceName, name -> {
String target = serviceDiscovery.resolveServiceAddress(name);
return createManagedChannel(target);
});
}

private ManagedChannel createManagedChannel(String target) {
ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
.usePlaintext() // Prod'da TLS kullanın
.enableRetry()
.maxRetryAttempts(3)
.build();

    setupConnectionMonitoring(channel, target);
    return channel;
}

private void setupConnectionMonitoring(ManagedChannel channel, String target) {
channel.notifyWhenStateChanged(ConnectivityState.TRANSIENT_FAILURE, () -> {
logger.warn("Connection lost to {}", target);
scheduleReconnection(channel, target);
});

    channel.notifyWhenStateChanged(ConnectivityState.READY, () -> {
        logger.info("Connection established to {}", target);
    });
}

private void scheduleReconnection(ManagedChannel channel, String target) {
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(() -> {
try {
channel.resetConnectBackoff();
logger.info("Attempting to reconnect to {}", target);
} catch (Exception e) {
logger.error("Reconnection attempt failed", e);
}
}, 1, 5, TimeUnit.SECONDS); // 1 sn sonra başla, her 5 sn'de bir dene
}
Servis Keşfi (Service Discovery)
java
public interface ServiceDiscovery {
String resolveServiceAddress(String serviceName);
}

// Örnek bir Consul tabanlı implementasyon
public class ConsulServiceDiscovery implements ServiceDiscovery {
private final ConsulClient consulClient;
private final String datacenter;

    public ConsulServiceDiscovery(String consulHost, int consulPort, String datacenter) {
        this.consulClient = new ConsulClient(consulHost, consulPort);
        this.datacenter = datacenter;
    }
    
    @Override
    public String resolveServiceAddress(String serviceName) {
        Response<List<HealthService>> response = consulClient.getHealthServices(
            serviceName, true, QueryParams.DEFAULT, datacenter);
        
        List<HealthService> services = response.getValue();
        if (services.isEmpty()) {
            throw new RuntimeException("No healthy instances found for service: " + serviceName);
        }
        
        // Basit bir round-robin load balancing
        HealthService service = services.get(ThreadLocalRandom.current().nextInt(services.size()));
        return service.getService().getAddress() + ":" + service.getService().getPort();
    }
}
Bağlantı Havuzu Yönetimi
java
public class ConnectionPoolConfig {
private int maxConnections = 10;
private long idleTimeoutMinutes = 30;
private boolean keepAlive = true;
private long keepAliveTimeMinutes = 5;

    // Getter ve Setter metodları...
}

public class GrpcStubPool<T extends AbstractStub<T>> {
private final BlockingQueue<T> pool;
private final Supplier<T> stubFactory;

    public GrpcStubPool(int size, Supplier<T> stubFactory) {
        this.pool = new ArrayBlockingQueue<>(size);
        this.stubFactory = stubFactory;
        initializePool(size);
    }
    
    private void initializePool(int size) {
        for (int i = 0; i < size; i++) {
            pool.add(stubFactory.get());
        }
    }
    
    public T borrowStub() throws InterruptedException {
        return pool.take();
    }
    
    public void returnStub(T stub) {
        if (stub != null) {
            pool.offer(stub);
        }
    }
}
Kullanım Örneği
java
public class Main {
public static void main(String[] args) {
// Servis keşfi yapılandırması
ServiceDiscovery discovery = new ConsulServiceDiscovery("localhost", 8500, "dc1");

        // Bağlantı havuzu yapılandırması
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxConnections(5);
        poolConfig.setKeepAlive(true);
        
        // gRPC istemci yöneticisi oluştur
        GrpcClientManager clientManager = new GrpcClientManager(discovery, poolConfig);
        
        // Servis stub'ı al
        GreeterGrpc.GreeterBlockingStub stub = clientManager.getStub(GreeterGrpc.GreeterBlockingStub.class);
        
        // RPC çağrısı yap
        HelloRequest request = HelloRequest.newBuilder().setName("World").build();
        HelloResponse response = stub.sayHello(request);
        System.out.println("Response: " + response.getMessage());
    }
}
Özellikler
Otomatik Yeniden Bağlanma: Bağlantı koptuğunda otomatik olarak yeniden bağlanmayı dener.

Servis Keşfi: Common microservice'leri dinamik olarak bulabilir.

Bağlantı Havuzu: Bağlantıları yönetir ve yeniden kullanılabilir hale getirir.

Kalıcı Bağlantılar: Bağlantılar uzun süreli olarak korunur.

Çoklu Servis Desteği: Farklı microservice'lere bağlantı kurulabilir.

Geliştirme Önerileri
TLS Desteği: Prod ortamında mutlaka TLS kullanın.

Metrikler: Bağlantı durumu, çağrı sayıları gibi metrikleri toplayın.

Hata Yönetimi: Çeşitli hata senaryolarına karşı dayanıklılık ekleyin.

Load Balancing: Daha gelişmiş load balancing algoritmaları ekleyin.

Circuit Breaker: Hata durumlarında devre kesici deseni uygulayın.

Bu tasarım, temel ihtiyaçlarınızı karşılayacak bir başlangıç noktası sağlar. Projenizin özel gereksinimlerine göre genişletebilir ve özelleştirebilirsiniz.