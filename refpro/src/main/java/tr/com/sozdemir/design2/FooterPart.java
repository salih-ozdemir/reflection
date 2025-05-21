package tr.com.sozdemir.design2;

class FooterPart extends MessagePart {
    private int crc;

    public FooterPart(int crc) {
        super(null); // Footer'ın BaseReqRes part'ı olmayabilir
        this.crc = crc;
    }

    @Override
    public byte[] toBytes() {
        return intToBytes(crc);
    }

    private byte[] intToBytes(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value
        };
    }
}