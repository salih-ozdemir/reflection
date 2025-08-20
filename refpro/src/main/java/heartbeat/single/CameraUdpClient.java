package heartbeat.single;


import java.net.*;
import java.util.concurrent.CompletableFuture;

public class CameraUdpClient {

    private final InetAddress address;
    private final int port;
    private final DatagramSocket socket;

    public CameraUdpClient(String host, int port) throws Exception {
        this.address = InetAddress.getByName(host);
        this.port = port;
        this.socket = new DatagramSocket(); // tek socket
        this.socket.setSoTimeout(2000);
    }

    public CompletableFuture<Void> sendCommandAsync(String message) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] buf = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
                socket.send(packet);
            } catch (Exception e) {
                throw new RuntimeException("UDP send failed", e);
            }
        });
    }

    public boolean isConnected() {
        return !socket.isClosed();
    }

    public void close() {
        socket.close();
    }
}