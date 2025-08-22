Bana şimdi şöyle bir yapı lazım udpconnection olacak  burada komutlar için queue kullanacağım. Async bir bağlantı yapacağım. Ben grpc ile haberlesiyorum.  Bu async gelen mesejalari da grpc den tekrar göndermek istiyorum.
ChatGPT:

Anladım 👍 senin sisteminde üç katman var:

UDP Connection (kamera ile konuşan)

Kameraya komut gönderiyor.

Cevapları dinliyor.

Queue ile komut akışını düzenliyor.

gRPC Service (microservice arası iletişim)

Dış dünyadan gelen komutları alıyor (GrpcCommandService).

UDPConnection’a push ediyor (queue’ya atıyor).

UDP’den gelen async cevapları tekrar gRPC client’a publish ediyor.

Komut Kuyruğu + Async İşleme

BlockingQueue ile “backpressure” sağlıyorsun (kameraya aşırı yüklenmiyorsun).

UDP’den gelen mesajları da asenkron bir listener thread işliyor.

🔹 Önerilen Genel Yapı
// UDP bağlantısını yöneten sınıf
public class UdpConnection {
private final BlockingQueue<CameraCommand> commandQueue = new LinkedBlockingQueue<>();
private final ExecutorService executor = Executors.newSingleThreadExecutor();
private DatagramSocket socket;

    public UdpConnection(int port) throws SocketException {
        this.socket = new DatagramSocket(port);
        startCommandWorker();
        startResponseListener();
    }

    // Kuyruğa komut at
    public CompletableFuture<String> sendCommand(CameraCommand cmd) {
        CompletableFuture<String> future = new CompletableFuture<>();
        cmd.setFuture(future); // Komut ile future eşleşiyor
        commandQueue.offer(cmd);
        return future;
    }

    // Kuyruktaki komutları kameraya gönder
    private void startCommandWorker() {
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                CameraCommand cmd = commandQueue.take();
                byte[] data = cmd.toBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, cmd.getAddress(), cmd.getPort());
                socket.send(packet);
                // Burada cevap gelirse future tamamlanacak (listener tarafından)
            }
        });
    }

    // Kameradan async cevapları dinle
    private void startResponseListener() {
        Executors.newSingleThreadExecutor().submit(() -> {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String response = new String(packet.getData(), 0, packet.getLength());

                // Cevabı ilgili future ile eşle
                handleResponse(response);
            }
        });
    }

    private void handleResponse(String response) {
        // response içindeki ID’ye göre future bul
        CameraCommand cmd = CommandRegistry.match(response);
        if (cmd != null) {
            cmd.getFuture().complete(response);
        }
    }
}

🔹 Komut Nesnesi
public class CameraCommand {
private final String id;
private final String payload;
private final InetAddress address;
private final int port;
private CompletableFuture<String> future;

    // getter/setter
    public byte[] toBytes() {
        return payload.getBytes(StandardCharsets.UTF_8);
    }
}

🔹 gRPC Service Tarafı
@GrpcService
public class CameraGrpcService extends CameraServiceGrpc.CameraServiceImplBase {
private final UdpConnection udpConnection;

    public CameraGrpcService(UdpConnection udpConnection) {
        this.udpConnection = udpConnection;
    }

    @Override
    public void sendCommand(CommandRequest request, StreamObserver<CommandResponse> responseObserver) {
        CameraCommand cmd = new CameraCommand(
            UUID.randomUUID().toString(),
            request.getPayload(),
            InetAddress.getByName(request.getIp()),
            request.getPort()
        );

        udpConnection.sendCommand(cmd).thenAccept(response -> {
            CommandResponse resp = CommandResponse.newBuilder()
                    .setResult(response)
                    .build();
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        }).exceptionally(ex -> {
            responseObserver.onError(ex);
            return null;
        });
    }
}

🔹 Çalışma Mantığı

gRPC client sendCommand çağırır.

Komut UdpConnection’daki kuyruğa eklenir.

Worker thread bu komutu UDP üzerinden kameraya yollar.

UDP listener kameradan cevap gelince doğru CompletableFuture’ı tamamlar.

Future tamamlanınca gRPC cevabı client’a döner.

Böylece:

Komut akışı asenkron çalışıyor.

Queue ile fazla komut birikirse sırayla gidiyor.

gRPC’den gelen request/response future ile bağlanıyor.

👉 Sana şunu sorayım: UDP’den her gelen cevabı request–response eşleştirmesi mi yapacaksın (yani her komutun cevabı var mı), yoksa bazı UDP paketleri push/notify gibi bağımsız mı olacak? (örneğin kamera status event atıyorsa)

Şunu dedin:
Evet her sorunun cevabı var
ChatGPT:

Harika 👍 o zaman yapı çok daha temiz olur çünkü her gönderilen komutun bir cevabı var diyorsun. Bu durumda UDP tarafını sen request/response RPC gibi işletebilirsin.

Bunu sağlamanın 2 ana yolu var:

🔹 1. Komut ID ile Eşleştirme (En Sağlıklısı)

Her CameraCommand oluşturulduğunda benzersiz bir ID (UUID, counter, timestamp+nonce) eklenir.

UDP’ye giden paketin başına bu ID yazılır.

Kamera cevabı döndüğünde aynı ID’yi içerir.

Listener gelen paketi parse edip Map<id, future> üzerinden doğru CompletableFuture’ı tamamlar.

Örnek:

class UdpConnection {
private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<String> sendCommand(CameraCommand cmd) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(cmd.getId(), future);

        // UDP ile gönder
        byte[] data = cmd.toBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, cmd.getAddress(), cmd.getPort());
        socket.send(packet);

        return future;
    }

    private void handleResponse(String response) {
        String id = extractId(response); // response içinden ID parse et
        CompletableFuture<String> f = pending.remove(id);
        if (f != null) {
            f.complete(response);
        }
    }
}


Avantaj:
✅ Komutlar asenkron da gitse doğru eşleşir.
✅ Joystick flood (çok fazla left) durumunda bile en günceli kalır.

🔹 2. Kuyruk + “Son Komut Override” (Joystick için Optimize)

Joystick gibi sürekli akış yapan komutlarda, ID takibiyle değil, tip bazlı overwrite yapabilirsin.
Mesela: left kuyruğa 5 tane eklenirse sadece sonuncuyu tut.

Bunun için:

ConcurrentLinkedQueue yerine ConcurrentHashMap<commandType, CameraCommand> kullanırsın.

Worker thread sırayla bu map’ten en güncel komutu alır.

🔹 Senin Durumuna Uygun Senaryo

Normal komutlar (zoom, preset, focus, vb.) → ID ile request/response eşleşme

Joystick hareket komutları (pan/tilt) → overwrite/drop mekanizması

Yani aslında tek bir UdpConnection içinde iki mod olacak:

pending map → Request/Response matching için

latestCommandMap → Flood edilebilecek joystick komutları için

İstersen ben sana hem ID bazlı normal komutlar hem de overwrite/drop joystick komutları için birleşik bir UdpConnection sınıfı tasarlayayım.

İster misin?

Şunu dedin:
Evet
ChatGPT:

Süper 👍 o zaman sana birleşik UdpConnection yapısını çıkarayım. Bu yapı:

Request/Response (ID eşleşmesi) → Normal komutlar için (zoom, preset, focus vs.)

Overwrite/Drop (tip bazlı en günceli tutma) → Joystick komutları için (left, right, up, down vs.)

📌 Birleşik UdpConnection Tasarımı
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class UdpConnection {

    private final DatagramSocket socket;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Request/Response eşleştirme (ID → future)
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    // Joystick komutları için overwrite (tip → komut)
    private final ConcurrentHashMap<String, CameraCommand> latestJoystickCommands = new ConcurrentHashMap<>();

    public UdpConnection(int port) throws Exception {
        this.socket = new DatagramSocket(port);
        startListener();
        startJoystickWorker();
    }

    /**
     * Normal komut: ID ile gönderilir, response beklenir.
     */
    public CompletableFuture<String> sendCommand(CameraCommand cmd, InetAddress addr, int port) throws Exception {
        String id = UUID.randomUUID().toString();
        cmd.setId(id);
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(id, future);

        byte[] data = cmd.toBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        socket.send(packet);

        return future;
    }

    /**
     * Joystick komutu: Kuyruğa değil, tip bazlı overwrite edilir.
     */
    public void sendJoystickCommand(CameraCommand cmd) {
        latestJoystickCommands.put(cmd.getType(), cmd);
    }

    /**
     * UDP'den gelen response'ları dinle.
     */
    private void startListener() {
        executor.submit(() -> {
            byte[] buf = new byte[1024];
            while (!socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                // Response'tan ID çıkar
                String id = extractId(response);
                if (id != null && pending.containsKey(id)) {
                    CompletableFuture<String> f = pending.remove(id);
                    if (f != null) {
                        f.complete(response);
                    }
                } else {
                    System.out.println("Joystick/async data geldi: " + response);
                }
            }
        });
    }

    /**
     * Joystick worker: sürekli latestJoystickCommands içinden en güncel komutları çeker.
     */
    private void startJoystickWorker() {
        executor.submit(() -> {
            while (!socket.isClosed()) {
                for (Map.Entry<String, CameraCommand> entry : latestJoystickCommands.entrySet()) {
                    CameraCommand cmd = entry.getValue();
                    byte[] data = cmd.toBytes();
                    DatagramPacket packet = new DatagramPacket(data, data.length, cmd.getAddress(), cmd.getPort());
                    socket.send(packet);
                }
                Thread.sleep(50); // joystick akışı ~20Hz
            }
        });
    }

    private String extractId(String response) {
        // Response içinden ID ayıklama mantığını burada kur
        if (response.startsWith("ID:")) {
            return response.split(" ")[0].substring(3);
        }
        return null;
    }

    public void close() {
        socket.close();
        executor.shutdownNow();
    }
}

📌 Açıklama

Normal komutlar (sendCommand)

UUID ile ID atanıyor.

Future geri dönüyor → gRPC katmanı bu future’dan async cevap alıyor.

UDP cevabı geldiğinde ID eşleşiyor → future.complete(response).

Joystick komutları (sendJoystickCommand)

Kuyruk yerine ConcurrentHashMap<type, CameraCommand> → sadece en güncel komut kalıyor.

Worker thread 50ms aralıkla (20Hz) bunları kameraya gönderiyor.

Fazla komut flood’u otomatik drop oluyor.

Listener

Gelen UDP cevaplarını ID bazlı eşleştiriyor.

Joystick cevapları veya “push” veriler varsa onları logluyor (veya başka yere publish edebilirsin → gRPC event, Kafka vs.).

👉 Bu yapıyı senin gRPC layer içine bağladığında:

gRPC üzerinden gelen her komut → sendCommand ya da sendJoystickCommand çağrısı

Response gerekiyorsa CompletableFuture’dan client’a async döndürülür

Joystick flood sorununu çözmek için ayrı thread son komutları drip-feed ediyor

İstersen ben bunun üstüne bir de Spring Boot bean yapısı kurup,
@Service UdpConnectionManager içinde kameraları IP/Port bazlı yönetelim mi?

Şunu dedin:
Thread.sleep biraz sikintili
ChatGPT:

Aynen 👍 çok doğru yakaladın. Thread.sleep() UDP connection worker gibi kritik yerde iyi bir çözüm değil çünkü:

Tüm thread’i blokluyor → concurrency verimsiz olur.

JVM thread pool içindeysen (mesela @Async, ForkJoinPool, Netty EventLoop vs.), event loop’u dondurur, başka işlere izin vermez.

Time drift (sleep süresi garanti değil) yüzünden joystick frekansın kayabilir.

✅ Daha Doğru Çözümler

Joystick için "sabit frekansta en güncel komutu gönderme" mantığını şöyle yapabilirsin:

1. ScheduledExecutorService ile
   private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

private void startJoystickWorker() {
scheduler.scheduleAtFixedRate(() -> {
try {
for (Map.Entry<String, CameraCommand> entry : latestJoystickCommands.entrySet()) {
CameraCommand cmd = entry.getValue();
byte[] data = cmd.toBytes();
DatagramPacket packet = new DatagramPacket(data, data.length, cmd.getAddress(), cmd.getPort());
socket.send(packet);
}
} catch (Exception e) {
e.printStackTrace();
}
}, 0, 50, TimeUnit.MILLISECONDS); // 20Hz
}


👉 Burada Thread.sleep yok. Scheduler tam zamanında çalışır, CPU’yu boşa meşgul etmez.

2. Reactor / Project Loom (ileri seviye)

Eğer reactive kullanıyorsan (Reactor/Netty),
Flux.interval(Duration.ofMillis(50)) kullanarak non-blocking şekilde 50ms tick oluşturabilirsin.
Bu tick’te son joystick komutlarını gönderirsin.

Flux.interval(Duration.ofMillis(50))
.subscribe(tick -> {
latestJoystickCommands.values().forEach(cmd -> sendUdp(cmd));
});

3. Event Bazlı

Joystick komutlarını queue’da bekletip,
her yeni komut geldiğinde timer resetleyen bir mekanizma kurabilirsin.
Böylece boş yere her 50ms tick atmazsın, sadece joystick aktifken gönderirsin.

👉 Özet:
Thread.sleep yerine ScheduledExecutorService.scheduleAtFixedRate kullan → en temiz, thread-blocking olmayan, deterministik çözüm.

İstersen ben sana yukarıdaki UdpConnection sınıfını Thread.sleep yerine scheduler ile güncellenmiş halini vereyim mi?

Şunu dedin:
Evet
ChatGPT:

Süper 👍 o zaman UdpConnection sınıfını Thread.sleep yerine ScheduledExecutorService ile güncelleyip sana async + queue destekli bir örnek yapayım:

import java.net.*;
import java.util.Map;
import java.util.concurrent.*;

public class UdpConnection {
private final DatagramSocket socket;
private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Kuyruk: en güncel komutları saklar (ör: joystick için)
    private final ConcurrentMap<String, CameraCommand> latestCommands = new ConcurrentHashMap<>();

    public UdpConnection(int localPort) throws SocketException {
        this.socket = new DatagramSocket(localPort);
        this.socket.setSoTimeout(1000); // UDP response beklerken timeout
    }

    // Komut ekle (ör: joystick komutları)
    public void enqueueCommand(String key, CameraCommand cmd) {
        latestCommands.put(key, cmd); // aynı key gelirse eskisi overwrite edilir (drop mekanizması)
    }

    // UDP paket gönder
    private void sendCommand(CameraCommand cmd) {
        try {
            byte[] data = cmd.toBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, cmd.getAddress(), cmd.getPort()
            );
            socket.send(packet);
            System.out.println("Sent: " + cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Arka planda belirli aralıklarla kuyruğu boşalt
    public void startCommandScheduler(long intervalMs) {
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, CameraCommand> entry : latestCommands.entrySet()) {
                sendCommand(entry.getValue());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    // UDP response dinleme (async)
    public void startListening() {
        Executors.newSingleThreadExecutor().submit(() -> {
            byte[] buf = new byte[1024];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String response = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Received: " + response);
                    // burada grpc'ye publish edebilirsin
                } catch (SocketTimeoutException e) {
                    // timeout normal (heartbeat kontrolü için kullanılabilir)
                }
            }
        });
    }

    public void close() {
        scheduler.shutdownNow();
        socket.close();
    }
}

🔑 Öne Çıkanlar:

Queue yerine ConcurrentMap → aynı komut tipinde (örn: LEFT) son gelen overwrite ediyor.

Böylece left left left left right right → sadece left ve right kalır.

scheduleAtFixedRate → Thread.sleep yok, CPU boşa kilitlenmez.

Dinleme thread’i ayrı → response paketlerini asenkron alıp istersen gRPC’ye publish edebilirsin.

Kapatma → scheduler.shutdownNow() + socket.close()

👉 Bu yapı joystick için ama istersen diğer kamera komutlarını da aynı queue/scheduler mekanizmasına dahil edebilirsin.

İster misin ben sana joystick ve normal komutları ayırabileceğin (priority queue + latest map) destekli versiyonunu da yazayım?

Şunu dedin:
Evet
ChatGPT:

Harika 👌 O zaman sana 2 katmanlı queue yapısı vereyim:

Joystick komutları (örn. LEFT/RIGHT/UP/DOWN)

Bunlar sürekli geliyor → en günceli önemli.

ConcurrentMap kullanıyoruz → aynı key’den (örn. LEFT) sadece en son gelen kalıyor.

Normal komutlar (örn. ZOOM-IN, SNAPSHOT, CONFIG)

Bunlar kritik, kaybolmamalı.

BlockingQueue (FIFO) kullanıyoruz → sırayla gönderiliyor.

🟢 Kod Örneği
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;

public class UdpConnection {
private final DatagramSocket socket;
private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Joystick kuyruğu → overwrite mode (aynı key en güncel kalır)
    private final ConcurrentMap<String, CameraCommand> joystickCommands = new ConcurrentHashMap<>();

    // Normal komut kuyruğu → FIFO
    private final BlockingQueue<CameraCommand> normalCommands = new LinkedBlockingQueue<>();

    public UdpConnection(int localPort) throws SocketException {
        this.socket = new DatagramSocket(localPort);
        this.socket.setSoTimeout(1000); // UDP response beklerken timeout
    }

    // JOYSTICK komut ekle
    public void enqueueJoystickCommand(String key, CameraCommand cmd) {
        joystickCommands.put(key, cmd);
    }

    // NORMAL komut ekle
    public void enqueueNormalCommand(CameraCommand cmd) {
        normalCommands.offer(cmd);
    }

    // UDP gönderme
    private void sendCommand(CameraCommand cmd) {
        try {
            byte[] data = cmd.toBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, cmd.getAddress(), cmd.getPort()
            );
            socket.send(packet);
            System.out.println("Sent: " + cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Joystick komutlarını belirli aralıklarla flush et
    public void startJoystickScheduler(long intervalMs) {
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, CameraCommand> entry : joystickCommands.entrySet()) {
                sendCommand(entry.getValue());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    // Normal komutları sırayla işleme
    public void startNormalCommandProcessor() {
        scheduler.submit(() -> {
            while (!socket.isClosed()) {
                try {
                    CameraCommand cmd = normalCommands.take(); // blocking FIFO
                    sendCommand(cmd);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    // UDP response dinleme
    public void startListening() {
        Executors.newSingleThreadExecutor().submit(() -> {
            byte[] buf = new byte[1024];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String response = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Received: " + response);

                    // burada gRPC publish yapılabilir
                } catch (SocketTimeoutException e) {
                    // timeout normal
                }
            }
        });
    }

    public void close() {
        scheduler.shutdownNow();
        socket.close();
    }
}

🔑 Avantajlar

Joystick → overwrite → eski komutlar çöpe, sadece en günceli kalıyor.

Normal komutlar → FIFO → kritik komutlar asla kaybolmaz, sırayla işlenir.

İkisi aynı anda çalışıyor → joystick sürekli flush edilirken, normal komutlar sırayla yürür.

Tam async → hiçbir yerde Thread.sleep yok, her şey scheduler veya BlockingQueue ile kontrol ediliyor.

👉 İstersen ben sana bunun gRPC tarafıyla nasıl bağlanacağını da (yani gelen GrpcCommand → enqueueJoystickCommand veya enqueueNormalCommand) bir örnek verebilirim. Onu da ister misin?

Şunu dedin:
Evet
ChatGPT:

Süper 👍 Şimdi sana gRPC tarafı ile UDP async queue bağlanmış bir yapı kuralım.

Senin sisteminde senaryo şu şekilde:

Client → gRPC → Backend (komut gönderiyor).

Backend gelen komutu UDP üzerinden kameraya gönderiyor.

Kameradan UDP response gelirse → tekrar gRPC stream ile client’a publish edilebiliyor.

🟢 Proto Tanımı (örnek)
syntax = "proto3";

service CameraControlService {
// Client kameraya komut yollar
rpc SendCommand(CameraCommandRequest) returns (CommandAck);

// Kameradan gelen yanıtlar stream olarak client’a gider
rpc ListenResponses(Empty) returns (stream CameraResponse);
}

message CameraCommandRequest {
string type = 1;       // "JOYSTICK" veya "NORMAL"
string key = 2;        // "LEFT", "RIGHT" vs.
string payload = 3;    // opsiyonel, başka parametreler
}

message CommandAck {
string status = 1; // "QUEUED", "ERROR"
}

message CameraResponse {
string payload = 1;
}

message Empty {}

🟢 gRPC Servis Implementasyonu (Java + Spring Boot)
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CopyOnWriteArrayList;

public class CameraControlServiceImpl extends CameraControlServiceGrpc.CameraControlServiceImplBase {

    private final UdpConnection udpConnection;
    private final CopyOnWriteArrayList<StreamObserver<CameraResponse>> responseObservers = new CopyOnWriteArrayList<>();

    public CameraControlServiceImpl(UdpConnection udpConnection) {
        this.udpConnection = udpConnection;

        // UDP listener -> gRPC subscriber'a push
        udpConnection.startListening(response -> {
            CameraResponse resp = CameraResponse.newBuilder()
                    .setPayload(response)
                    .build();
            for (StreamObserver<CameraResponse> obs : responseObservers) {
                obs.onNext(resp);
            }
        });
    }

    @Override
    public void sendCommand(CameraCommandRequest request, StreamObserver<CommandAck> responseObserver) {
        try {
            CameraCommand cmd = new CameraCommand(request.getKey(), request.getPayload());

            if ("JOYSTICK".equalsIgnoreCase(request.getType())) {
                udpConnection.enqueueJoystickCommand(request.getKey(), cmd);
            } else {
                udpConnection.enqueueNormalCommand(cmd);
            }

            responseObserver.onNext(CommandAck.newBuilder()
                    .setStatus("QUEUED")
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onNext(CommandAck.newBuilder()
                    .setStatus("ERROR: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listenResponses(Empty request, StreamObserver<CameraResponse> responseObserver) {
        responseObservers.add(responseObserver);
    }
}

🟢 UdpConnection (UDP + callback)

UdpConnection içinde UDP response’u yakalayıp gRPC’ye publish edelim.

public class UdpConnection {
// ... önceki kod ...

    // Callback listener
    public interface ResponseListener {
        void onResponse(String response);
    }

    public void startListening(ResponseListener listener) {
        Executors.newSingleThreadExecutor().submit(() -> {
            byte[] buf = new byte[1024];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String response = new String(packet.getData(), 0, packet.getLength());
                    listener.onResponse(response);
                } catch (SocketTimeoutException e) {
                    // normal
                }
            }
        });
    }
}

🔑 Artık elimizde:

Async queue yapısı

Joystick → overwrite

Normal → FIFO

UDP ↔ gRPC köprüsü

SendCommand → UDP queue

ListenResponses → UDP’den gelen paketleri client’a push

👉 İstersen bir sonraki adımda ben sana bunun Spring Boot starter/bean konfigürasyonunu (yani @Bean UdpConnection + @GrpcService CameraControlServiceImpl) nasıl yapacağını gösterebilirim. Onu ister misin?

Şunu dedin:
Hayır udpconnection benim library yani genelledigim kod. Sistemimde çağırıyorum kullanıyorum. Bu cagirdigim yerde queue veya async için dinleyecek sınıfları yazmam gerek
ChatGPT:

Anladım 👍 senin UdpConnection zaten generic library → yani UDP soketi açma, mesaj gönderme/recv yapma işini hallediyor.
Bunu kullandığın yerde (mesela kamera adapter servisi) senin kendi queue + async executor + consumer sınıflarını yazman gerekiyor.

O zaman yapı şöyle olacak:

🔹 Katmanlı Yapı

Library (UdpConnection)

Temel UDP send/receive işini yapar.

send(byte[]), startListening(callback) gibi generic API’ler sağlar.

İçinde queue yok, tamamen “stateless socket handler”.

Adapter Servis (senin yazacağın)

CommandQueueManager → FIFO / overwrite queue yönetimi.

AsyncCommandDispatcher → Queue’dan alıp UdpConnection.send() ile gönderir.

UdpResponsePublisher → UdpConnection.startListening() callback’ini dinler, event’i gRPC stream’e basar.

🔹 Örnek Kod: Queue Manager
import java.util.concurrent.*;
import java.util.*;

public class CommandQueueManager {

    private final BlockingQueue<CameraCommand> normalQueue = new LinkedBlockingQueue<>();
    private final Map<String, CameraCommand> joystickLatest = new ConcurrentHashMap<>();

    public void enqueue(CameraCommand cmd) {
        if ("JOYSTICK".equalsIgnoreCase(cmd.getType())) {
            // overwrite logic
            joystickLatest.put(cmd.getKey(), cmd);
        } else {
            // FIFO
            normalQueue.offer(cmd);
        }
    }

    public CameraCommand takeNext() throws InterruptedException {
        // önce joystick varsa onu verelim
        if (!joystickLatest.isEmpty()) {
            Iterator<Map.Entry<String, CameraCommand>> it = joystickLatest.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<String, CameraCommand> entry = it.next();
                it.remove(); // tek seferlik al
                return entry.getValue();
            }
        }
        // yoksa normal komutlardan sıradaki
        return normalQueue.take();
    }
}

🔹 Async Dispatcher (Queue → UDP)
import java.util.concurrent.*;

public class AsyncCommandDispatcher {
private final CommandQueueManager queueManager;
private final UdpConnection udpConnection;
private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AsyncCommandDispatcher(CommandQueueManager queueManager, UdpConnection udpConnection) {
        this.queueManager = queueManager;
        this.udpConnection = udpConnection;
    }

    public void start() {
        executor.submit(() -> {
            while (true) {
                try {
                    CameraCommand cmd = queueManager.takeNext();
                    udpConnection.send(cmd.toUdpBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

🔹 UDP Response Listener (UDP → gRPC Publish)
import io.grpc.stub.StreamObserver;

public class UdpResponsePublisher {
private final UdpConnection udpConnection;

    public UdpResponsePublisher(UdpConnection udpConnection) {
        this.udpConnection = udpConnection;
    }

    public void register(StreamObserver<CameraResponse> grpcObserver) {
        udpConnection.startListening(resp -> {
            grpcObserver.onNext(CameraResponse.newBuilder()
                    .setPayload(resp)
                    .build());
        });
    }
}


✅ Bu şekilde:

Senin UdpConnection hep generic kalıyor.

Queue + async + overwrite logic → tamamen CommandQueueManager ve AsyncCommandDispatcher içinde.

gRPC tarafı da sadece bu servisleri bean olarak inject edip kullanıyor.

👉 İstersen sana bunun Spring Boot içinde nasıl lifecycle yönetileceğini (@PostConstruct ile dispatcher.start(), @PreDestroy ile shutdown()) de göstereyim. Onu da ister misin?

Şunu dedin:
Udoconnectin genel bir yapı içinde yapmak istiyorum ve kütüphaneye koyacağım daha önceki ethernet ve grpc geneledigim yapıyi udpconnection ile genişletip daha sonra joystick ayrı diger konutlar priority bakımından ayırt edecek şekilde yazmak
ChatGPT:

Çok mantıklı 👍 senin zaten genel bir communication library fikrin var (daha önce EthernetConnection, GrpcClient gibi yaptın).
Şimdi aynı konsepti UdpConnection için de yapmak istiyorsun → yani common kütüphanede bir "base async connection + queue yönetimi + priority/overwrite logic" olacak.

🔹 Tasarım Önerisi

Senin yapın 3 katman olacak:

Core Connection Layer (Library)

AbstractConnection<T> → ortak davranış (connect, disconnect, send, startListening).

UdpConnection extends AbstractConnection<byte[]>.

GrpcConnection extends AbstractConnection<Message>.

EthernetConnection extends AbstractConnection<byte[]>.

Burada sadece I/O var → Queue / priority yok.

Message Queue Layer (Library)

CommandQueue<T> → FIFO + priority + overwrite (joystick gibi).

AsyncDispatcher<T> → Queue → Connection.send() mapping.

Bu da library’de olacak.

Böylece sen hangi connection’u kullanırsan kullan aynı CommandQueue/AsyncDispatcher yapısını bağlayabilirsin.

Service Layer (Uygulama)

Kamera adapter servisin veya joystick kontrolcün bu queue’yu doldurur.

Response listener’ı gRPC’ye veya event bus’a basar.

🔹 BaseConnection Arayüzü
public interface BaseConnection<T> {
void connect() throws Exception;
void disconnect();
void send(T message) throws Exception;
void startListening(Listener<T> listener);

    interface Listener<T> {
        void onMessage(T msg);
    }
}

🔹 UDP Connection
import java.net.*;
import java.util.concurrent.*;

public class UdpConnection implements BaseConnection<byte[]> {
private DatagramSocket socket;
private final String host;
private final int port;
private volatile boolean running = false;

    public UdpConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void connect() throws Exception {
        socket = new DatagramSocket();
        running = true;
    }

    @Override
    public void disconnect() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void send(byte[] message) throws Exception {
        DatagramPacket packet = new DatagramPacket(message, message.length, InetAddress.getByName(host), port);
        socket.send(packet);
    }

    @Override
    public void startListening(Listener<byte[]> listener) {
        Executors.newSingleThreadExecutor().submit(() -> {
            byte[] buffer = new byte[4096];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    listener.onMessage(packet.getData());
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }
        });
    }
}

🔹 Command Queue (Priority + Overwrite)
import java.util.concurrent.*;
import java.util.*;

public class CommandQueue<T extends PrioritizedCommand> {
private final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
private final Map<String, T> overwriteLatest = new ConcurrentHashMap<>();

    public void enqueue(T cmd) {
        if (cmd.isOverwrite()) {
            overwriteLatest.put(cmd.getKey(), cmd);
        } else {
            queue.offer(cmd);
        }
    }

    public T takeNext() throws InterruptedException {
        // Öncelik: overwrite komutlar
        if (!overwriteLatest.isEmpty()) {
            Iterator<Map.Entry<String, T>> it = overwriteLatest.entrySet().iterator();
            if (it.hasNext()) {
                var entry = it.next();
                it.remove();
                return entry.getValue();
            }
        }
        return queue.take();
    }
}

🔹 PrioritizedCommand Interface
public interface PrioritizedCommand {
String getType();      // "JOYSTICK", "NORMAL"
String getKey();       // JOYSTICK için axis veya direction
boolean isOverwrite(); // true ise queue yerine overwrite
byte[] toUdpBytes();   // UDP gönderim için encode
}

🔹 Dispatcher (Generic)
import java.util.concurrent.*;

public class AsyncDispatcher<T extends PrioritizedCommand> {
private final CommandQueue<T> queue;
private final BaseConnection<byte[]> connection;
private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AsyncDispatcher(CommandQueue<T> queue, BaseConnection<byte[]> connection) {
        this.queue = queue;
        this.connection = connection;
    }

    public void start() {
        executor.submit(() -> {
            while (true) {
                try {
                    T cmd = queue.takeNext();
                    connection.send(cmd.toUdpBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

🔹 Senin Senaryo

UdpConnection → Library’de.

GrpcConnection ve EthernetConnection → zaten var.

CommandQueue + AsyncDispatcher → Library’de.

Kamera adapterinde:

CommandQueue<CameraCommand> yaratılır.

AsyncDispatcher<CameraCommand> → UdpConnection’a bağlanır.

gRPC → queue.enqueue(command) çağırır.

UdpConnection.startListening() → gelen response’u gRPC.onNext() ile publish eder.

✅ Böylece senin UDP, Ethernet, gRPC hepsi aynı base yapıya oturuyor.
✅ Queue mantığı sadece library’de bir kere yazılıyor.
✅ Joystick overwrite logic → PrioritizedCommand implementasyonu ile çözülebiliyor.

İstersem sana bunun Spring Boot Starter gibi otomatik bean üreten halini de gösterebilirim (senin daha önce gRPC client için düşündüğün gibi).
👉 İster misin bu yapıyı Spring Boot Starter + annotation config seviyesine taşıyalım?