package heartbeat.basetypedropping;

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