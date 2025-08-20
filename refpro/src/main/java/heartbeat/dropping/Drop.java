package heartbeat.dropping;

/// 1️⃣ Tek Elemanlı Queue (Override Strategy)
///
/// Joystick komut kuyruğunu capacity=1 olacak şekilde tanımlıyoruz.
///
/// Yeni komut geldiğinde eskiyi siliyoruz → sadece en güncel kalıyor.
///
/// Böylece left left left left right right geldiğinde queue’da sadece son “right” kalır.
public class Drop {
    BlockingQueue<CameraCommand> ptzQueue = new ArrayBlockingQueue<>(1);

    public void enqueuePtz(CameraCommand cmd) {
        ptzQueue.clear();   // önceki komutları drop et
        ptzQueue.offer(cmd);
    }

}
