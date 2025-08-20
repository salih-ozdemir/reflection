package heartbeat.single;

package com.example.camera;

import java.time.Instant;

public class CameraCommand {
    private final CommandType type;
    private final String rawMessage;
    private final Instant timestamp;

    public CameraCommand(CommandType type, String rawMessage) {
        this.type = type;
        this.rawMessage = rawMessage;
        this.timestamp = Instant.now();
    }

    public CommandType getType() { return type; }
    public String getRawMessage() { return rawMessage; }
    public Instant getTimestamp() { return timestamp; }
}
