package heartbeat.basetypedropping;

@RestController
@RequestMapping("/joystick")
public class JoystickController {

    private final JoystickCommandQueue commandQueue;

    public JoystickController(JoystickCommandQueue commandQueue) {
        this.commandQueue = commandQueue;
    }

    @PostMapping("/moveLeft")
    public void moveLeft() {
        commandQueue.enqueue(new CameraCommand("AA_LEFT", CommandCategory.PAN_TILT));
    }

    @PostMapping("/moveRight")
    public void moveRight() {
        commandQueue.enqueue(new CameraCommand("AA_RIGHT", CommandCategory.PAN_TILT));
    }

    @PostMapping("/zoomIn")
    public void zoomIn() {
        commandQueue.enqueue(new CameraCommand("AA_ZOOM_IN", CommandCategory.ZOOM));
    }
}