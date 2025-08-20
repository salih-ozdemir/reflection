package heartbeat.multicamera;

// com.example.camera.config.CameraConfig
public class CameraConfig {
    private final String id;      // benzersiz kamera ID
    private final String host;
    private final int port;

    public CameraConfig(String id, String host, int port) {
        this.id = id; this.host = host; this.port = port;
    }
    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
}