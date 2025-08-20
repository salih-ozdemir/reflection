package heartbeat.single;

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