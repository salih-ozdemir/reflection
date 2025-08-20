package heartbeat.single;

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
///@EnableScheduling eklemeyi unutma (SpringBootApplication içine)