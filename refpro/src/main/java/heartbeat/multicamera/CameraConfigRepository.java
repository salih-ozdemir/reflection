package heartbeat.multicamera;

// com.example.camera.config.CameraConfigRepository
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class CameraConfigRepository {
    // Gerçekte DB/Config Server’dan okuyabilirsin
    private final Map<String, CameraConfig> configs = Map.of(
            "cam-1", new CameraConfig("cam-1", "10.0.0.11", 9001),
            "cam-2", new CameraConfig("cam-2", "10.0.0.12", 9002)
    );

    public Optional<CameraConfig> findById(String id) {
        return Optional.ofNullable(configs.get(id));
    }

    public Collection<CameraConfig> findAll() { return configs.values(); }
}