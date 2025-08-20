package heartbeat.basetypedropping;

public class CameraCommand {
    private final String rawMessage;          // Kameraya gidecek UDP/TCP komut
    private final CommandCategory category;   // Komut tipi
    private final long timestamp;             // En son geleni seçebilmek için

    public CameraCommand(String rawMessage, CommandCategory category) {
        this.rawMessage = rawMessage;
        this.category = category;
        this.timestamp = System.currentTimeMillis();
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public CommandCategory getCategory() {
        return category;
    }

    public long getTimestamp() {
        return timestamp;
    }
}