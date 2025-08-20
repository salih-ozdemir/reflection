👉 İstersen bir sonraki adımda ben sana birden fazla kamera için (manager/factory yapısı) da ekleyeyim, yani her kamera kendi UDP client + kuyruğu olacak şekilde. Onu da ister misin?

Şunu dedin:
Evet
ChatGPT:

Süper—şimdi yapıyı çoklu kamera için genel hale getirelim. Her kameranın kendi:

UDP client’ı

PTZ ve CONTROL kuyruğu

Worker thread’leri
olsun; CameraManager bunları singleton olarak yönetsin.

Aşağıdaki parçalar “plug&play” mantığıyla Spring Boot projesine eklenebilir.

1) Kamera Tanımı (Config)
   // com.example.camera.config.CameraConfig
   public class CameraConfig {
   private final String id;      // benzersiz kamera ID
   private final String host;
   private final int port;

   public CameraConfig(String id, String host, int port) {
   this.id = id; this.host = host; this.port = port;
   }
   public String getId() { return id; }
   public String getHost() { return host; }
   public int getPort() { return port; }
   }


Örnek kaynak (DB, YAML vs. yerine basit liste):

// com.example.camera.config.CameraConfigRepository
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class CameraConfigRepository {
// Gerçekte DB/Config Server’dan okuyabilirsin
private final Map<String, CameraConfig> configs = Map.of(
"cam-1", new CameraConfig("cam-1", "10.0.0.11", 9001),
"cam-2", new CameraConfig("cam-2", "10.0.0.12", 9002)
);

    public Optional<CameraConfig> findById(String id) {
        return Optional.ofNullable(configs.get(id));
    }

    public Collection<CameraConfig> findAll() { return configs.values(); }
}

2) Komut Modeli ve Tipi
   // com.example.camera.core.CommandType
   public enum CommandType { PTZ, CONTROL, HEARTBEAT }

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

3) UDP Client (tek soket, async send)
   // com.example.camera.net.CameraUdpClient
   import java.net.*;
   import java.util.concurrent.CompletableFuture;

public class CameraUdpClient {
private final InetAddress addr;
private final int port;
private final DatagramSocket socket;

    public CameraUdpClient(String host, int port) throws Exception {
        this.addr = InetAddress.getByName(host);
        this.port = port;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(2000);
    }

    public CompletableFuture<Void> sendAsync(String msg) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] buf = msg.getBytes();
                socket.send(new DatagramPacket(buf, buf.length, addr, port));
            } catch (Exception e) {
                throw new RuntimeException("UDP send failed", e);
            }
        });
    }

    public boolean isOpen() { return !socket.isClosed(); }
    public void close() { socket.close(); }
}

4) Kamera “Context” (client + kuyruklar + worker’lar)
   // com.example.camera.runtime.CameraRuntime
   import java.util.concurrent.*;

public class CameraRuntime {
public final CameraConfig cfg;
public final CameraUdpClient client;

    // PTZ: sadece en güncel komut kalsın (flood önleme)
    public final BlockingQueue<CameraCommand> ptzQ = new ArrayBlockingQueue<>(1);
    // CONTROL: FIFO, kayıp yok
    public final BlockingQueue<CameraCommand> controlQ = new LinkedBlockingQueue<>();

    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private volatile boolean running = true;

    public CameraRuntime(CameraConfig cfg) throws Exception {
        this.cfg = cfg;
        this.client = new CameraUdpClient(cfg.getHost(), cfg.getPort());
        startWorkers();
    }

    private void startWorkers() {
        // PTZ worker – her zaman en son komut
        workers.submit(() -> {
            while (running) {
                CameraCommand cmd = ptzQ.take();
                client.sendAsync(cmd.getRawMessage())
                      .orTimeout(700, TimeUnit.MILLISECONDS)
                      .exceptionally(ex -> { /* log */ return null; })
                      .join(); // sıraya alınmış tek iş
            }
        });

        // CONTROL worker – sırayı koru, kayıp olmasın
        workers.submit(() -> {
            while (running) {
                CameraCommand cmd = controlQ.take();
                client.sendAsync(cmd.getRawMessage())
                      .orTimeout(1500, TimeUnit.MILLISECONDS)
                      .exceptionally(ex -> { /* log */ return null; })
                      .join(); // sıra garantisi
            }
        });
    }

    public void stop() {
        running = false;
        workers.shutdownNow();
        client.close();
    }
}

5) CameraManager (singleton havuzu)
   // com.example.camera.runtime.CameraManager
   import org.springframework.stereotype.Component;
   import java.util.concurrent.ConcurrentHashMap;

@Component
public class CameraManager {
private final CameraConfigRepository repo;
private final ConcurrentHashMap<String, CameraRuntime> pool = new ConcurrentHashMap<>();

    public CameraManager(CameraConfigRepository repo) {
        this.repo = repo;
    }

    public CameraRuntime getOrCreate(String cameraId) {
        return pool.computeIfAbsent(cameraId, id -> {
            var cfg = repo.findById(id)
                          .orElseThrow(() -> new IllegalArgumentException("Unknown camera: " + id));
            try { return new CameraRuntime(cfg); }
            catch (Exception e) { throw new RuntimeException("Create runtime failed: " + id, e); }
        });
    }

    public void stopAll() { pool.values().forEach(CameraRuntime::stop); }
}

6) Komut Servisi (tek giriş noktası)
   // com.example.camera.app.CameraCommandService
   import org.springframework.stereotype.Service;

@Service
public class CameraCommandService {
private final CameraManager manager;

    public CameraCommandService(CameraManager manager) {
        this.manager = manager;
    }

    public void enqueue(String cameraId, CameraCommand cmd) {
        var rt = manager.getOrCreate(cameraId);
        switch (cmd.getType()) {
            case PTZ -> { rt.ptzQ.clear(); rt.ptzQ.offer(cmd); } // hep en son
            case CONTROL, HEARTBEAT -> rt.controlQ.offer(cmd);   // sırayla
        }
    }
}

7) Heartbeat (kamera başına)
   // com.example.camera.app.HeartbeatScheduler
   import org.springframework.scheduling.annotation.Scheduled;
   import org.springframework.stereotype.Component;

@Component
public class HeartbeatScheduler {
private final CameraCommandService svc;
private final CameraConfigRepository repo;

    public HeartbeatScheduler(CameraCommandService svc, CameraConfigRepository repo) {
        this.svc = svc; this.repo = repo;
    }

    // Her 5 sn tüm kameralara heartbeat
    @Scheduled(fixedRate = 5000)
    public void sendHeartbeats() {
        repo.findAll().forEach(cfg -> {
            String msg = "AA_HEARTBEAT:" + cfg.getId(); // AA formatına uygun
            svc.enqueue(cfg.getId(), new CameraCommand(CommandType.HEARTBEAT, msg));
        });
    }
}


@EnableScheduling’i ana uygulamaya eklemeyi unutma.

8) Örnek Controller (PTZ ve CONTROL)
   // com.example.camera.api.CameraController
   import org.springframework.http.ResponseEntity;
   import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/camera")
public class CameraController {

    private final CameraCommandService svc;

    public CameraController(CameraCommandService svc) { this.svc = svc; }

    @PostMapping("/{cameraId}/ptz/pan-left")
    public ResponseEntity<Void> panLeft(@PathVariable String cameraId, @RequestParam(defaultValue = "5") int speed) {
        // AA protokolüne uygun örnek: AA01 = PAN_LEFT
        String msg = "AA01:" + speed;
        svc.enqueue(cameraId, new CameraCommand(CommandType.PTZ, msg));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{cameraId}/control/preset/{index}")
    public ResponseEntity<Void> goPreset(@PathVariable String cameraId, @PathVariable int index) {
        // AA10 = Goto Preset
        String msg = "AA10:" + index;
        svc.enqueue(cameraId, new CameraCommand(CommandType.CONTROL, msg));
        return ResponseEntity.ok().build();
    }
}

Tasarım Notları

Gerçek eşleşme & cevap okuma: Kamera push etmiyorsa bu örnekte receive yok. Cevap okuyacaksan, her kamera için listener thread ekleyip gelen paketleri işle.

AA başlığı / mesaj çerçevesi: Önerim, AA protokolünde komut kodu + opsiyonel sequence kullanman (örn. AA01|seq=123|speed=5). Böylece gelecekte request/response eşleştirme kolay olur.

QoS: PTZ kuyruğu tek elemanlı → her zaman en güncel komut işlenir (joystick için ideal). CONTROL kuyruğu sınırsız değilse, makul bir kapasite verip taşmada logla.

Shutdown: Uygulama kapanışında CameraManager.stopAll() çağır.

Çok kamera / performans: Çok kamerada worker’lar kamera başına 2 thread. Gerekirse virtual threads (Java 21) ile maliyeti azaltabilirsin.

Hazır 👌 Bu iskeletle çoklu kamerayı tek bir API’den, singleton kamera context’leriyle, PTZ ve CONTROL önceliklendirmesiyle ve heartbeat’lerle yönetebilirsin.
İstersen bir sonraki adımda AA mesajlarının encode/decode (CRC, length, seq, opcode) çerçevesini birlikte netleştirelim; böylece UDP’de kayıp/yanlış okuma risklerini de minimize ederiz.