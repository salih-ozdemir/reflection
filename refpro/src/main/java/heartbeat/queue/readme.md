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

Şunu dedin:
bu queue mekanizmasını joystick dışında da kullansam olur mu
ChatGPT:

Kesinlikle 👍 sadece joystick için değil, tüm yüksek frekanslı / çakışma ihtimali olan komut setleri için bu kuyruk yapısı uygundur.

Şöyle düşünebilirsin:

Joystick → çok sık ve sürekli akışlı komut (left, right, zoom in/out). Burada “önceki” komutun bir anlamı yok, hep en güncel lazım.

Heartbeat → belirli aralıklarla gönderilen tekrar eden komut. Kuyrukta varsa eskisini silip en günceli bırakabilirsin.

Durum Sorguları (status, telemetry) → hızlıca ardı ardına gelebilir. Burada da aynı sorgudan 10 tane sırada beklemesin, sadece en güncel olan kalsın.

PTZ preset çağırma gibi kritik komutlar → bunlarda drop edilmemesi gerekir. Kuyrukta bekleyip sırası gelince çalışmalı.

📌 Önerilen yaklaşım

Kuyruk mantığını kategori bazlı genelleştirebilirsin:

public enum CommandMode {
DROP_OLD,   // Joystick, heartbeat
KEEP_ALL    // Preset, kayıt başlat/durdur gibi kritik işler
}


Komut sınıfına ekleyelim:

public class CameraCommand {
private final String rawMessage;
private final CommandCategory category;
private final CommandMode mode;

    public CameraCommand(String rawMessage, CommandCategory category, CommandMode mode) {
        this.rawMessage = rawMessage;
        this.category = category;
        this.mode = mode;
    }

    public String getRawMessage() { return rawMessage; }
    public CommandCategory getCategory() { return category; }
    public CommandMode getMode() { return mode; }
}


Queue tarafında:

public synchronized void enqueue(CameraCommand cmd) {
if (cmd.getMode() == CommandMode.DROP_OLD) {
// aynı kategoridekileri sil
queue.removeIf(existing -> existing.getCategory() == cmd.getCategory());
}
queue.offer(cmd);
}

📌 Böylece:

Joystick / heartbeat → DROP_OLD → sadece son gelen kalır.

Preset çağırma / kayıt başlatma → KEEP_ALL → sırayla hepsi işlenir.

👉 İster misin ben sana bu yapıyı JoystickCommandQueue yerine genel amaçlı CameraCommandQueue haline getirip, joystick dahil tüm sistemin kullanabileceği hale getireyim?

Şunu dedin:
evet
ChatGPT:

Harika 👍 O zaman JoystickCommandQueue’yu genelleştirip her türlü kamera komutunu işleyebilecek hale getiriyoruz. Böylece joystick de, heartbeat de, preset çağırma da aynı yapı üzerinden geçer.

Aşağıda sana CameraCommandQueue örneğini yazdım:

📌 Komut Tanımı
public enum CommandCategory {
JOYSTICK,
HEARTBEAT,
PRESET,
RECORDING,
STATUS
}

public enum CommandMode {
DROP_OLD,   // sadece sonuncu kalsın
KEEP_ALL    // sırayla hepsi işlensin
}

public class CameraCommand {
private final String rawMessage;
private final CommandCategory category;
private final CommandMode mode;

    public CameraCommand(String rawMessage, CommandCategory category, CommandMode mode) {
        this.rawMessage = rawMessage;
        this.category = category;
        this.mode = mode;
    }

    public String getRawMessage() { return rawMessage; }
    public CommandCategory getCategory() { return category; }
    public CommandMode getMode() { return mode; }
}

📌 Genel Kuyruk
import java.util.concurrent.ConcurrentLinkedQueue;

public class CameraCommandQueue {
private final ConcurrentLinkedQueue<CameraCommand> queue = new ConcurrentLinkedQueue<>();

    public synchronized void enqueue(CameraCommand cmd) {
        if (cmd.getMode() == CommandMode.DROP_OLD) {
            // Aynı kategorideki eski komutları drop et
            queue.removeIf(existing -> existing.getCategory() == cmd.getCategory());
        }
        queue.offer(cmd);
    }

    public CameraCommand dequeue() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}

📌 Kullanım Örnekleri
CameraCommandQueue queue = new CameraCommandQueue();

// Joystick komutları (sadece sonuncu önemli)
queue.enqueue(new CameraCommand("LEFT", CommandCategory.JOYSTICK, CommandMode.DROP_OLD));
queue.enqueue(new CameraCommand("LEFT", CommandCategory.JOYSTICK, CommandMode.DROP_OLD));
queue.enqueue(new CameraCommand("RIGHT", CommandCategory.JOYSTICK, CommandMode.DROP_OLD));
// Kuyrukta sadece "RIGHT" kalır

// Preset çağırma (kritik, sırayla çalıştırılmalı)
queue.enqueue(new CameraCommand("GOTO_PRESET_1", CommandCategory.PRESET, CommandMode.KEEP_ALL));
queue.enqueue(new CameraCommand("GOTO_PRESET_2", CommandCategory.PRESET, CommandMode.KEEP_ALL));
// Kuyrukta ikisi de kalır, sırayla işlenir

📌 Avantajlar

Tek Queue → tüm komut tiplerini tek yapı üzerinden yönetiyorsun.

Kategori bazlı kontrol → hangi komutun drop edileceği, hangisinin sırayla gideceği belli.

Joystick + diğer mesajlar aynı sistemde uyumlu çalışıyor.

Gelecekte priority (öncelik) eklemek de kolay olur.

👉 İstersen sana bir de queue worker (arkada sürekli dequeue eden ve UDP/TCP üzerinden kameraya gönderen thread) örneği yazayım mı? Böylece sen sadece enqueue yaparsın, gönderim otomatik akar.