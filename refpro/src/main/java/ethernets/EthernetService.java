package ethernets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class EthernetService {

    @Value("${ethernet.server.ip}")
    private String serverIp;

    @Value("${ethernet.server.port}")
    private int serverPort;

    @Value("${ethernet.reconnect.delay.seconds:5}")
    private int reconnectDelaySeconds;

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Thread dataReceiverThread;
    private Consumer<byte[]> dataReceivedListener; // Listener for incoming data

    public enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        CONNECTING
    }

    @PostConstruct
    public void init() {
        // Start connection attempt asynchronously after the service is initialized
        scheduler.execute(this::attemptConnection);
    }

    @PreDestroy
    public void destroy() {
        disconnect();
        scheduler.shutdownNow(); // Immediately shut down the scheduler
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void setDataReceivedListener(Consumer<byte[]> listener) {
        this.dataReceivedListener = listener;
    }

    private synchronized void attemptConnection() {
        if (connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.CONNECTING) {
            return; // Already connected or attempting to connect
        }

        System.out.println("Attempting to connect to Ethernet device at " + serverIp + ":" + serverPort);
        connectionStatus = ConnectionStatus.CONNECTING;

        while (connectionStatus != ConnectionStatus.CONNECTED) {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, serverPort), 5000); // 5 second connection timeout
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
                connectionStatus = ConnectionStatus.CONNECTED;
                System.out.println("Successfully connected to Ethernet device.");
                startDataReceiver();
                break; // Exit loop on successful connection
            } catch (IOException e) {
                System.err.println("Failed to connect to Ethernet device: " + e.getMessage());
                disconnect(); // Ensure resources are closed if connection fails
                System.out.println("Retrying connection in " + reconnectDelaySeconds + " seconds...");
                try {
                    Thread.sleep(reconnectDelaySeconds * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Connection retry interrupted.");
                    break; // Exit if interrupted
                }
            }
        }
    }

    public synchronized void disconnect() {
        if (connectionStatus == ConnectionStatus.DISCONNECTED) {
            return;
        }

        System.out.println("Disconnecting from Ethernet device...");
        connectionStatus = ConnectionStatus.DISCONNECTED; // Set status early to prevent new operations

        if (dataReceiverThread != null && dataReceiverThread.isAlive()) {
            dataReceiverThread.interrupt(); // Stop the receiver thread
            try {
                dataReceiverThread.join(1000); // Wait for the thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("Disconnected from Ethernet device.");
        } catch (IOException e) {
            System.err.println("Error during disconnection: " + e.getMessage());
        } finally {
            inputStream = null;
            outputStream = null;
            socket = null;
        }
    }

    public void sendData(byte[] data) throws IOException {
        if (connectionStatus != ConnectionStatus.CONNECTED || outputStream == null) {
            throw new IOException("Not connected to Ethernet device.");
        }
        try {
            outputStream.write(data);
            outputStream.flush();
            System.out.println("Sent " + data.length + " bytes.");
        } catch (IOException e) {
            System.err.println("Error sending data: " + e.getMessage());
            handleDisconnection(e);
            throw e; // Re-throw to inform caller
        }
    }

    private void startDataReceiver() {
        if (dataReceiverThread != null && dataReceiverThread.isAlive()) {
            dataReceiverThread.interrupt(); // Ensure old thread is stopped
        }
        dataReceiverThread = new Thread(() -> {
            byte[] buffer = new byte[1024]; // Or a larger buffer size as needed
            int bytesRead;
            System.out.println("Starting data receiver thread...");
            try {
                while (connectionStatus == ConnectionStatus.CONNECTED && !Thread.currentThread().isInterrupted()) {
                    // This read is blocking. It will wait for data or throw an exception.
                    bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                        // End of stream, connection gracefully closed by peer
                        System.out.println("End of stream reached. Peer closed connection.");
                        handleDisconnection(null);
                        break;
                    }
                    byte[] receivedData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, receivedData, 0, bytesRead);
                    System.out.println("Received " + bytesRead + " bytes: " + new String(receivedData)); // For demonstration
                    if (dataReceivedListener != null) {
                        dataReceivedListener.accept(receivedData);
                    }
                }
            } catch (IOException e) {
                if (connectionStatus == ConnectionStatus.CONNECTED) { // Only log error if we thought we were connected
                    System.err.println("Error receiving data: " + e.getMessage());
                    handleDisconnection(e);
                }
            } catch (Exception e) {
                System.err.println("Unexpected error in data receiver: " + e.getMessage());
                handleDisconnection(e);
            } finally {
                if (connectionStatus == ConnectionStatus.CONNECTED) { // If connection was active, try to reconnect
                    System.out.println("Data receiver thread stopped. Reattempting connection...");
                    scheduler.execute(this::attemptConnection);
                }
            }
        }, "Ethernet-Data-Receiver");
        dataReceiverThread.start();
    }

    private void handleDisconnection(Exception e) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            System.out.println("Connection lost. Initiating reconnection...");
            disconnect();
            // Schedule reconnection attempt after a delay
            scheduler.schedule(this::attemptConnection, reconnectDelaySeconds, TimeUnit.SECONDS);
        }
    }
}