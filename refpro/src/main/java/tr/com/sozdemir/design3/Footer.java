package tr.com.sozdemir.design3;

public class Footer extends MessagePart {
    private String crc;

    public Footer(String dataToChecksum) {
        this.crc = calculateCRC(dataToChecksum);
    }

    private String calculateCRC(String data) {
        // Basit bir örnek, gerçek uygulama için CRC32 veya benzeri algoritma
        return Integer.toHexString(data.hashCode());
    }

    // Getters
}