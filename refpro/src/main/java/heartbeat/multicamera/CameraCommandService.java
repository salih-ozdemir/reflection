package heartbeat.multicamera;

// com.example.camera.app.CameraCommandService
import org.springframework.stereotype.Service;

@Service
public class CameraCommandService {
    private final CameraManager manager;

    public CameraCommandService(CameraManager manager) {
        this.manager = manager;
    }

    public void enqueue(String cameraId, CameraCommand cmd) {
        var rt = manager.getOrCreate(cameraId);
        switch (cmd.getType()) {
            case PTZ -> { rt.ptzQ.clear(); rt.ptzQ.offer(cmd); } // hep en son
            case CONTROL, HEARTBEAT -> rt.controlQ.offer(cmd);   // sırayla
        }
    }
}