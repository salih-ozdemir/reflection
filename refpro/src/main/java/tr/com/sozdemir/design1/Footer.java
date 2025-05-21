package tr.com.sozdemir.design1;

// Footer bölümü
class Footer implements MessageSection {
    private final long crc;

    public Footer(long crc) {
        this.crc = crc;
    }

    public long getCrc() {
        return crc;
    }

    @Override
    public byte[] toBytes() {
        // CRC değerini byte dizisine çevirme
        byte[] result = new byte[8];
        for (int i = 0; i < 8; i++) {
            result[i] = (byte)(crc >>> (i * 8));
        }
        return result;
    }

    @Override
    public void validate() throws InvalidMessageException {
        // CRC için basit bir doğrulama
        if (crc < 0) {
            throw new InvalidMessageException("Invalid CRC value");
        }
    }
}
