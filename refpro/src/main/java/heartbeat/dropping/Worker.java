package heartbeat.dropping;


///
/// 3️⃣ Worker Çalışma Mantığı
///
/// Worker hep queue’dan alıp gönderir:
public class Worker {
    Executors.newSingleThreadExecutor().submit(() -> {
        while (true) {
            CameraCommand cmd = ptzQueue.take();
            client.sendCommandAsync(cmd.getRawMessage());
        }
    });
}
