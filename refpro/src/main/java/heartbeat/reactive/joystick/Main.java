package heartbeat.reactive.joystick;

public class Main {
    public static void main(String[] args) throws Exception {
        UdpConnection udp = new UdpConnection("127.0.0.1", 9000);
        CommandDispatcher dispatcher = new CommandDispatcher(udp);

        // Joystick komutları → sadece en güncel gider
        dispatcher.submit(new Command(CommandType.JOYSTICK, "LEFT"));
        dispatcher.submit(new Command(CommandType.JOYSTICK, "LEFT"));
        dispatcher.submit(new Command(CommandType.JOYSTICK, "RIGHT")); // sadece RIGHT gider

        // Task komutları → sırayla gider
        dispatcher.submit(new Command(CommandType.TASK, "ZOOM_IN"));
        dispatcher.submit(new Command(CommandType.TASK, "ZOOM_OUT"));
    }
}
