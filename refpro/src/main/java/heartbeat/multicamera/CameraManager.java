package heartbeat.multicamera;

// com.example.camera.runtime.CameraManager
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CameraManager {
    private final CameraConfigRepository repo;
    private final ConcurrentHashMap<String, CameraRuntime> pool = new ConcurrentHashMap<>();

    public CameraManager(CameraConfigRepository repo) {
        this.repo = repo;
    }

    public CameraRuntime getOrCreate(String cameraId) {
        return pool.computeIfAbsent(cameraId, id -> {
            var cfg = repo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown camera: " + id));
            try { return new CameraRuntime(cfg); }
            catch (Exception e) { throw new RuntimeException("Create runtime failed: " + id, e); }
        });
    }

    public void stopAll() { pool.values().forEach(CameraRuntime::stop); }
}