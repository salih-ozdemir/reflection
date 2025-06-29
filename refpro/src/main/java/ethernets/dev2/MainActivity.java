package ethernets.dev2;

// Ana Uygulama Sınıfı (farklı bir yerden çağırma)
public class MainActivity {
    public static void main(String[] args) {
        DataManager manager = new DataManager();

        // setDataReceivedListener metodunu farklı bir yerden (MainActivity) çağırıyoruz
        manager.setDataReceivedListener(new DataListener() {
            @Override
            public void onDataReceived(String data) {
                System.out.println("Veri alındı: " + data);
            }
        });

        manager.simulateDataReception(); // Veri simülasyonunu başlat
    }
}