package heartbeat.multicamera;

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