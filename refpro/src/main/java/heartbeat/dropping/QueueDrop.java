package heartbeat.dropping;


/// 2️⃣ Son Komutu Tip Bazlı Replace Etme
///
/// Bazen left → right → zoom gibi farklı tipler de olabilir.
///
/// Eğer son gelen komut aynı kategoriye (örn. pan hareketleri) aitse, eskileri drop et.
///
/// Farklı kategori ise sakla.
public class QueueDrop {
    Deque<CameraCommand> ptzQueue = new LinkedList<>();

    public synchronized void enqueuePtz(CameraCommand cmd) {
        // Eğer kuyrukta aynı tipte varsa eskiyi sil
        ptzQueue.removeIf(existing -> existing.getType() == cmd.getType());
        ptzQueue.offer(cmd);
    }
}

///Böylece:
///
/// left left left right right → sadece son right kalır.
///
/// Ama left zoom → ikisi de kalır (çünkü biri pan, biri zoom).