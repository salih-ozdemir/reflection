görebilir miyim
ChatGPT:

Süper 👍 O zaman sana hem joystick (son state modeli) hem de task komutları (queue modeli) destekleyen bir UdpConnection + CommandDispatcher örneği hazırlayayım.

Aşağıdaki tasarım kütüphane gibi kullanılabilir:

import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

// Komut tipi
enum CommandType { JOYSTICK, TASK }

// Komut modeli
class Command {
final CommandType type;
final String payload;

    Command(CommandType type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    byte[] toBytes() {
        return payload.getBytes(); // UDP ile gönderilecek binary dönüşümü
    }

    @Override
    public String toString() {
        return type + ":" + payload;
    }
}

// UDP bağlantısı (temel)
class UdpConnection {
private final DatagramSocket socket;
private final InetAddress address;
private final int port;

    UdpConnection(String host, int port) throws Exception {
        this.socket = new DatagramSocket();
        this.address = InetAddress.getByName(host);
        this.port = port;
    }

    public void send(byte[] data) throws Exception {
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }
}

// Dispatcher: hem Joystick hem Task komutları için
class CommandDispatcher {
private final UdpConnection udp;

    // JOYSTICK → sadece en güncel state
    private final AtomicReference<Command> latestJoystickCommand = new AtomicReference<>();

    // TASK → sırayla işlenecek kuyruk
    private final BlockingQueue<Command> taskQueue = new LinkedBlockingQueue<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public CommandDispatcher(UdpConnection udp) {
        this.udp = udp;

        // Joystick handler (50ms periyodik en güncel komutu yollar)
        scheduler.scheduleAtFixedRate(() -> {
            Command cmd = latestJoystickCommand.getAndSet(null);
            if (cmd != null) {
                try {
                    udp.send(cmd.toBytes());
                    System.out.println("[JOYSTICK] Sent: " + cmd);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 50, TimeUnit.MILLISECONDS);

        // Task handler (FIFO queue)
        scheduler.submit(() -> {
            while (true) {
                try {
                    Command cmd = taskQueue.take(); // bloklayıcı
                    udp.send(cmd.toBytes());
                    System.out.println("[TASK] Sent: " + cmd);

                    // Burada ack bekleniyorsa Future / Callback eklenebilir
                    Thread.sleep(100); // örnek için pacing
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void submit(Command command) {
        if (command.type == CommandType.JOYSTICK) {
            latestJoystickCommand.set(command);
        } else {
            taskQueue.offer(command);
        }
    }
}

🚀 Kullanım Örneği
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

✅ Avantajlar

Joystick → AtomicReference sayesinde sadece son yön yollanır, CPU/network tasarrufu.

Task → FIFO queue ile tüm komutlar sırayla işlenir.

Async & Non-blocking → Thread.sleep() sadece task pacing için (ack ile değiştirilebilir).

👉 İstersen ben buna ack eşleştirme (ör. request gönder → response ACK al → sıradaki task’a geç) mekanizmasını da ekleyebilirim.
Bunu da görmek ister misin?