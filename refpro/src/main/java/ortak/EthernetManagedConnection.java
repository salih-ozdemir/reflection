package ortak;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EthernetManagedConnection implements ManagedConnection {
    private final String host;
    private final int port;
    private Socket socket;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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
                // For a more robust connection, consider timeouts and retry logic here
                socket = new Socket(host, port);
                System.out.println("Ethernet connected to: " + host + ":" + port);
                // Start a background task to monitor connection health if needed
                startHealthCheck();
            } catch (IOException e) {
                throw new ConnectionException("Failed to connect Ethernet to " + getIdentifier(), e);
            }
        }
    }

    @Override
    public void disconnect() {
        if (isConnected()) {
            System.out.println("Disconnecting Ethernet from: " + host + ":" + port);
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing Ethernet socket: " + e.getMessage());
            } finally {
                socket = null;
                scheduler.shutdownNow(); // Stop health check
            }
        }
    }

    // Simple health check for automatic reconnection
    private void startHealthCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isConnected()) {
                System.out.println("Ethernet connection lost for " + getIdentifier() + ". Attempting to reconnect...");
                try {
                    disconnect(); // Clean up old socket if any
                    connect();
                } catch (ConnectionException e) {
                    System.err.println("Failed to reconnect Ethernet: " + e.getMessage());
                    // Implement exponential backoff or circuit breaker here
                }
            }
        }, 0, 5, TimeUnit.SECONDS); // Check every 5 seconds
    }

    public Socket getSocket() {
        return socket;
    }
}
