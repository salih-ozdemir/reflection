İstersen bir sonraki adımda Joystick Queue + Drop mekanizmasını bu sendJoystickCommand içine nasıl entegre edeceğini gösterebilirim.

Bunu ister misin?

Şunu dedin:
evet görebilirim
ChatGPT:

Harika 🚀 O zaman şimdi mevcut yapımıza Joystick için Queue + Drop mekanizması ekleyelim.

Burada hedefimiz:

Normal komutlar (ör: Zoom, Focus vb.) → ACK beklenir → sendCommand()

Joystick komutları (ör: LEFT, RIGHT, UP, DOWN) → sürekli akar → sadece sonuncusu önemli → sendJoystickCommand()

🔹 Joystick Queue Executor
public class JoystickCommandExecutor {
private final BlockingQueue<Command> queue = new LinkedBlockingQueue<>();
private final UdpConnection udp;

    public JoystickCommandExecutor(UdpConnection udp) {
        this.udp = udp;
        startWorker();
    }

    public void enqueue(Command cmd) {
        // Aynı tipten eski komutları drop et
        queue.removeIf(c -> c.getType().equals(cmd.getType()));

        // Yeni komutu ekle
        queue.offer(cmd);
    }

    private void startWorker() {
        Thread worker = new Thread(() -> {
            try {
                while (true) {
                    Command cmd = queue.take(); // sıradaki komutu al
                    udp.send(cmd.toBytes());

                    // Çok sık gönderim olmasın diye küçük bir throttle
                    Thread.sleep(50); 
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Joystick-Worker");
        worker.setDaemon(true);
        worker.start();
    }
}

🔹 UdpCameraClient İçinde Kullanımı
public class UdpCameraClient {
private final UdpConnection udp;
private final PendingRequestManager pending = new PendingRequestManager();
private final JoystickCommandExecutor joystickExecutor;

    public UdpCameraClient(UdpConnection udp) {
        this.udp = udp;
        this.joystickExecutor = new JoystickCommandExecutor(udp);
        this.udp.startListening(this::onMessage);
    }

    public CompletableFuture<Response> sendCommand(Command cmd) {
        CompletableFuture<Response> future = pending.register(cmd.getType());
        udp.send(cmd.toBytes());

        future.orTimeout(2, TimeUnit.SECONDS)
              .exceptionally(ex -> {
                  System.err.println("Timeout: " + cmd);
                  return null;
              });

        return future;
    }

    public void sendJoystickCommand(Command cmd) {
        joystickExecutor.enqueue(cmd);
    }

    private void onMessage(byte[] msg) {
        Response res = Response.fromBytes(msg);
        pending.complete(res.getCommandType(), res);
    }
}

🔹 Kullanım Örneği
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

🔹 Ne Kazandık?

✅ Joystick flood yapmaz, sadece son LEFT/RIGHT/UP/DOWN kuyrukta kalır

✅ Queue fire-and-forget çalışır → ACK beklemez

✅ Normal komutlarla tamamen ayrışmış bir mekanizma

👉 Burada queue.removeIf() ile aynı tip eski komutları drop ettik.
Ama istersen daha akıllı bir kural da yazabiliriz (örneğin sadece “aynı yönde” olanları drop et, farklı yönler tutulmaya devam etsin).

İstiyor musun bu kısmı daha da intelligent drop policy yapalım?

Şunu dedin:
Thread.sleep(50); tüm threadleri blocklamak istemiyorum
ChatGPT:

Çok doğru 👍 Thread.sleep(50) bloklayıcı olduğu için worker thread'i kitliyor. Özellikle senin kütüphanen async/future tabanlı olduğundan, mümkün olduğunca non-blocking çalışmalı.

Burada birkaç seçenek var:

🔹 1. ScheduledExecutorService ile Non-Blocking Throttle

Worker thread içinde sleep yerine planlı gönderim yapabilirsin:

public class JoystickCommandExecutor {
private final BlockingQueue<Command> queue = new LinkedBlockingQueue<>();
private final UdpConnection udp;
private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public JoystickCommandExecutor(UdpConnection udp) {
        this.udp = udp;
        startWorker();
    }

    public void enqueue(Command cmd) {
        // aynı tip komutları drop et
        queue.removeIf(c -> c.getType().equals(cmd.getType()));
        queue.offer(cmd);
    }

    private void startWorker() {
        scheduler.scheduleAtFixedRate(() -> {
            Command cmd = queue.poll(); // non-blocking al
            if (cmd != null) {
                udp.send(cmd.toBytes());
            }
        }, 0, 50, TimeUnit.MILLISECONDS); // her 50ms’de bir çalışır
    }
}


✅ Avantaj: Thread.sleep yok, thread boşta beklemiyor, scheduler tetikleyici.
✅ Queue boşsa poll() null döner → CPU yemiyor.

🔹 2. Reactive Yaklaşım (CompletableFuture + Delay)

Her komut gönderimini future zinciriyle throttle edebilirsin:

public void enqueue(Command cmd) {
queue.removeIf(c -> c.getType().equals(cmd.getType()));
queue.offer(cmd);
}

private void startWorker() {
CompletableFuture.runAsync(() -> {
while (true) {
try {
Command cmd = queue.take();
udp.send(cmd.toBytes());

                // sleep yerine async delay
                CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)
                        .execute(() -> {}); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });
}

🔹 3. Backpressure ile “Son Komut”u Saklamak

Aslında joystick'te sürekli komutları bekletmeye gerek yok. Son geleni bir AtomicReference<Command> içinde saklayıp scheduler ile periyodik gönderim daha doğru olur:

public class JoystickCommandExecutor {
private final AtomicReference<Command> latestCommand = new AtomicReference<>();
private final UdpConnection udp;
private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public JoystickCommandExecutor(UdpConnection udp) {
        this.udp = udp;
        startWorker();
    }

    public void enqueue(Command cmd) {
        latestCommand.set(cmd); // sadece sonuncusu saklanır
    }

    private void startWorker() {
        scheduler.scheduleAtFixedRate(() -> {
            Command cmd = latestCommand.getAndSet(null);
            if (cmd != null) {
                udp.send(cmd.toBytes());
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }
}


✅ Daha hafif: Kuyruk yok, sadece son komut var.
✅ Çok yoğun joystick spam’inde bile sadece en güncel yön gönderilir.

📌 Şimdi sana sormam lazım:
Joystick için gerçekten “bütün yön değişikliklerini sırayla” göndermen gerekiyor mu, yoksa sadece “en güncel yön” (örn. son LEFT veya RIGHT) önemli mi?

Çünkü ona göre queue mü, yoksa atomic reference (son komut modeli) mi seçeceğiz.