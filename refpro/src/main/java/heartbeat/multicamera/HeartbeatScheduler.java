package heartbeat.multicamera;

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
//@EnableScheduling’i ana uygulamaya eklemeyi unutma.