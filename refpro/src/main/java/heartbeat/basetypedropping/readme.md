İstersen ben sana bu tip bazlı drop mekanizmasını (Pan/Tilt komutları eskiyi drop etsin, Zoom komutları ayrı kalsın) Spring Boot uyumlu bir JoystickCommandQueue servisi olarak kodlayayım. İster misin?

Şunu dedin:
evet
ChatGPT:

Süper 👍 O zaman sana Spring Boot uyumlu, tip bazlı drop mekanizması ile çalışan bir JoystickCommandQueue servisi yazıyorum. Bu servis:

Komutları kategoriye göre (PAN/TILT vs ZOOM vs FOCUS) saklar,

Aynı kategoriden eski komutları drop eder,

Worker thread ile asenkron olarak kameraya gönderir.

📌 Enum & Command Model

Önce bir enum ile kategori tanımlıyoruz:

public enum CommandCategory {
PAN_TILT,  // left, right, up, down
ZOOM,      // zoom in/out
FOCUS,     // focus commands
OTHER
}


Komut sınıfı:

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

📌 JoystickCommandQueue Servisi

Burada ConcurrentLinkedDeque kullanıyoruz.

Kuyruğa yeni komut girince, aynı kategoridekileri siliyor.

Sadece en güncel olan kalıyor.

import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class JoystickCommandQueue {

    private final Deque<CameraCommand> queue = new ConcurrentLinkedDeque<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final CameraClient cameraClient; // UDP/TCP async client

    public JoystickCommandQueue(CameraClient cameraClient) {
        this.cameraClient = cameraClient;

        // Worker başlat
        worker.submit(this::processLoop);
    }

    // Kuyruğa komut ekleme
    public synchronized void enqueue(CameraCommand cmd) {
        // Aynı kategoride eski komutları drop et
        queue.removeIf(existing -> existing.getCategory() == cmd.getCategory());
        queue.offer(cmd);
    }

    // Worker thread
    private void processLoop() {
        while (true) {
            try {
                CameraCommand cmd = queue.poll();
                if (cmd != null) {
                    // Asenkron gönder
                    cameraClient.sendCommandAsync(cmd.getRawMessage())
                            .exceptionally(ex -> {
                                System.err.println("Komut gönderilemedi: " + ex.getMessage());
                                return null;
                            });
                }
                Thread.sleep(20); // Çok sık döngü olmasın
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

📌 Kullanım Örneği

Controller üzerinden joystick verisi geldiğinde çağırırsın:

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


✅ Böylece:

left left left right right → sadece son right gönderilir.

left zoom → hem left hem zoom korunur (çünkü farklı kategoriler).

👉 İster misin ben buna bir de heartbeat mekanizmasını ekleyeyim (örneğin worker boşsa her 5 saniyede bir HEARTBEAT komutu göndersin)?