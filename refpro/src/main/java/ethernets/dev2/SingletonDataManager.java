package ethernets.dev2;

public class SingletonDataManager {
    private static SingletonDataManager instance;
    private DataListener listener;

    private SingletonDataManager() {
        // Özel kurucu metod
    }

    public static synchronized SingletonDataManager getInstance() {
        if (instance == null) {
            instance = new SingletonDataManager();
        }
        return instance;
    }

    public void setDataReceivedListener(DataListener listener) {
        this.listener = listener;
    }

    public void notifyData(String data) {
        if (listener != null) {
            listener.onDataReceived(data);
        }
    }
}
