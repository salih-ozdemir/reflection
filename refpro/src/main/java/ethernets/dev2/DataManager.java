package ethernets.dev2;

// Veri Yöneticisi Sınıfı
public class DataManager {
    private DataListener listener;

    public void setDataReceivedListener(DataListener listener) {
        this.listener = listener;
    }

    public void simulateDataReception() {
        // Diyelim ki bir yerden veri geldi
        String receivedData = "Hello from server!";
        if (listener != null) {
            listener.onDataReceived(receivedData);
        }
    }
}