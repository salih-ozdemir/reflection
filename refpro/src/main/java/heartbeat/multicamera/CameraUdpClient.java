package heartbeat.multicamera;

// com.example.camera.net.CameraUdpClient
import java.net.*;
import java.util.concurrent.CompletableFuture;

public class CameraUdpClient {
    private final InetAddress addr;
    private final int port;
    private final DatagramSocket socket;

    public CameraUdpClient(String host, int port) throws Exception {
        this.addr = InetAddress.getByName(host);
        this.port = port;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(2000);
    }

    public CompletableFuture<Void> sendAsync(String msg) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] buf = msg.getBytes();
                socket.send(new DatagramPacket(buf, buf.length, addr, port));
            } catch (Exception e) {
                throw new RuntimeException("UDP send failed", e);
            }
        });
    }

    public boolean isOpen() { return !socket.isClosed(); }
    public void close() { socket.close(); }
}