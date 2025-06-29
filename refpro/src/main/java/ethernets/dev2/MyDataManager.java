package ethernets.dev2;

public class MyDataManager {
    private DataListener listener;

    public void setDataReceivedListener(DataListener listener) {
        this.listener = listener;
    }

    public void receiveData(String data) {
        // Veri alındıktan sonra dinleyiciye bildiriliyor
        if (listener != null) {
            listener.onDataReceived(data);
        }
    }

    // Başka bir metottan çağırma örneği
    public void processIncomingData(String rawData) {
        // Veriyi işleyin
        String processedData = rawData.toUpperCase();
        receiveData(processedData); // setDataReceivedListener'ı çağırmıyoruz, onun belirlediği mekanizmayı kullanıyoruz.
        // Aslında burada çağrılan 'receiveData' metodudur,
        // o da eğer ayarlanmışsa listener'ı çağırır.
    }
}