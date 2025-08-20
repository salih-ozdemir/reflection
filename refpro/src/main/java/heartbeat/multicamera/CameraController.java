package heartbeat.multicamera;

// com.example.camera.api.CameraController
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/camera")
public class CameraController {

    private final CameraCommandService svc;

    public CameraController(CameraCommandService svc) { this.svc = svc; }

    @PostMapping("/{cameraId}/ptz/pan-left")
    public ResponseEntity<Void> panLeft(@PathVariable String cameraId, @RequestParam(defaultValue = "5") int speed) {
        // AA protokolüne uygun örnek: AA01 = PAN_LEFT
        String msg = "AA01:" + speed;
        svc.enqueue(cameraId, new CameraCommand(CommandType.PTZ, msg));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{cameraId}/control/preset/{index}")
    public ResponseEntity<Void> goPreset(@PathVariable String cameraId, @PathVariable int index) {
        // AA10 = Goto Preset
        String msg = "AA10:" + index;
        svc.enqueue(cameraId, new CameraCommand(CommandType.CONTROL, msg));
        return ResponseEntity.ok().build();
    }
}