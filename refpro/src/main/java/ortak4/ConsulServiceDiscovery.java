package ortak4;

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