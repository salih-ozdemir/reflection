package ethernets.dev2;

// Farklı bir sınıftan çağırma
public class AnotherClass {
    public void setupListener() {
        SingletonDataManager.getInstance().setDataReceivedListener(new DataListener() {
            @Override
            public void onDataReceived(String data) {
                System.out.println("Singleton üzerinden veri alındı: " + data);
            }
        });
    }
}