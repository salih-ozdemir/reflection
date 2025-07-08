package ortak3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EthernetManagedConnection implements ManagedConnection {
    private final String host;
    private final int port;
    private Socket socket;
    private final ScheduledExecutorService healthCheckScheduler = Executors.newSingleThreadScheduledExecutor();

    public EthernetManagedConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String getIdentifier() {
        return host + ":" + port;
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void connect() throws ConnectionException {
        if (!isConnected()) {
            System.out.println("Connecting Ethernet to: " + host + ":" + port);
            try {
                // Bağlantı zaman aşımı ekleyelim
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000); // 5 saniye zaman aşımı
                System.out.println("Ethernet connected to: " + host + ":" + port);
            } catch (IOException e) {
                throw new ConnectionException("Failed to connect Ethernet to " + getIdentifier(), e);
            }
        }
    }

    @Override
    public void disconnect() {
        if (socket != null && !socket.isClosed()) {
            System.out.println("Disconnecting Ethernet from: " + host + ":" + port);
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing Ethernet socket: " + e.getMessage());
            } finally {
                socket = null;
            }
        }
    }

    // Harici UniversalConnectionManager tarafından çağrılacak bir health check mekanizması
    // Bu sınıfın kendi içinde periyodik kontrol yapması yerine, manager tarafından yönetilmesi daha uygun.
    // Ancak daha düşük seviyeli IO hatalarını yakalamak için burada da bir monitor thread olabilir.
    // Şimdilik manager'ın periyodik isConnected() kontrolüne güvenelim.

    public Socket getSocket() {
        return socket;
    }
}