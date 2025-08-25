package heartbeat.reactive;

public class usage {
    UdpCameraClient client = new UdpCameraClient(new UdpConnection("192.168.1.10", 5000));

// Normal komut
client.sendCommand(new Command("ZOOM_IN"))
            .thenAccept(res -> System.out.println("Ack geldi: " + res));

// Joystick komutları
client.sendJoystickCommand(new Command("LEFT"));
client.sendJoystickCommand(new Command("LEFT"));
client.sendJoystickCommand(new Command("LEFT"));
client.sendJoystickCommand(new Command("RIGHT"));
// → Queue içinde sadece SON LEFT ve RIGHT kalacak
}
