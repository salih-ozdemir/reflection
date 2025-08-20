package heartbeat.single;


public enum CommandType {
    PTZ,        // joystick hareketleri (AA01, AA02 vs)
    CONTROL,    // konfigürasyon, stream start/stop
    HEARTBEAT   // keepalive
}