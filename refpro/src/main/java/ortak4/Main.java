package ortak4;

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