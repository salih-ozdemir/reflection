package ortak4;

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
}