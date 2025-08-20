package heartbeat.multicamera;

// com.example.camera.core.CameraCommand
import java.time.Instant;

public class CameraCommand {
    private final CommandType type;
    private final String rawMessage; // "AA..." protokol stringi
    private final Instant ts = Instant.now();

    public CameraCommand(CommandType type, String rawMessage) {
        this.type = type; this.rawMessage = rawMessage;
    }
    public CommandType getType() { return type; }
    public String getRawMessage() { return rawMessage; }
    public Instant getTs() { return ts; }
}