👉 İstersen ben sana bu yapıyı Spring Boot Service + Scheduler + Async UDP Client olarak entegre edilmiş halde çıkarayım (senin AA mesaj yapını kullanarak). Bunu ister misin?

Şunu dedin:
Evet
ChatGPT:

Süper 👍 o zaman sana bu yapıyı Spring Boot Service olarak, UDP client + Scheduler + Queue yönetimi ile göstereyim.
Aşağıdaki mimari senin dediğin gibi AA ile başlayan mesajları tipine göre ayırıp en uygun şekilde işler:

📂 Yapı
src/main/java/com/example/camera
├── CameraCommand.java       // Komut modeli
├── CommandType.java         // Enum
├── CameraUdpClient.java     // UDP async client
├── CameraCommandService.java// Kuyruk + worker mantığı
└── HeartbeatScheduler.java  // Heartbeat job

🔹 1. Enum (Komut Tipleri)
package com.example.camera;

public enum CommandType {
PTZ,        // joystick hareketleri (AA01, AA02 vs)
CONTROL,    // konfigürasyon, stream start/stop
HEARTBEAT   // keepalive
}

🔹 2. Model (Komut)
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

🔹 3. UDP Client (Async)
package com.example.camera;

import java.net.*;
import java.util.concurrent.CompletableFuture;

public class CameraUdpClient {

    private final InetAddress address;
    private final int port;
    private final DatagramSocket socket;

    public CameraUdpClient(String host, int port) throws Exception {
        this.address = InetAddress.getByName(host);
        this.port = port;
        this.socket = new DatagramSocket(); // tek socket
        this.socket.setSoTimeout(2000);
    }

    public CompletableFuture<Void> sendCommandAsync(String message) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] buf = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
                socket.send(packet);
            } catch (Exception e) {
                throw new RuntimeException("UDP send failed", e);
            }
        });
    }

    public boolean isConnected() {
        return !socket.isClosed();
    }

    public void close() {
        socket.close();
    }
}

🔹 4. Service (Kuyruk + Worker)
package com.example.camera;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class CameraCommandService {

    private final BlockingQueue<CameraCommand> controlQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<CameraCommand> ptzQueue = new ArrayBlockingQueue<>(1);

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private CameraUdpClient udpClient;

    @PostConstruct
    public void init() throws Exception {
        this.udpClient = new CameraUdpClient("127.0.0.1", 9000); // örnek IP

        // PTZ worker
        executor.submit(() -> {
            while (true) {
                CameraCommand cmd = ptzQueue.take();
                udpClient.sendCommandAsync(cmd.getRawMessage());
            }
        });

        // Control worker
        executor.submit(() -> {
            while (true) {
                CameraCommand cmd = controlQueue.take();
                udpClient.sendCommandAsync(cmd.getRawMessage()).join(); // sırayla garanti
            }
        });
    }

    public void enqueue(CameraCommand cmd) {
        switch (cmd.getType()) {
            case PTZ -> {
                ptzQueue.clear();
                ptzQueue.offer(cmd);
            }
            case CONTROL -> controlQueue.offer(cmd);
            default -> {}
        }
    }
}

🔹 5. Heartbeat Scheduler
package com.example.camera;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatScheduler {

    private final CameraCommandService service;

    public HeartbeatScheduler(CameraCommandService service) {
        this.service = service;
    }

    @Scheduled(fixedRate = 5000) // her 5 saniyede bir
    public void sendHeartbeat() {
        CameraCommand heartbeat = new CameraCommand(CommandType.HEARTBEAT, "AA_HEARTBEAT");
        service.enqueue(heartbeat);
    }
}


@EnableScheduling eklemeyi unutma (SpringBootApplication içine).

📌 Özet

Joystick/PTZ → ArrayBlockingQueue(1) → hep en güncel komut kalıyor.

Kontrol mesajları → FIFO kuyruğu → sırası ile garanti.

Heartbeat → scheduler ile geliyor, service’e düşüyor.

UDP client async, ama CONTROL komutları için join() ile blocking sağlanıyor (sıra bozulmasın diye).

👉 İstersen bir sonraki adımda ben sana birden fazla kamera için (manager/factory yapısı) da ekleyeyim, yani her kamera kendi UDP client + kuyruğu olacak şekilde. Onu da ister misin?