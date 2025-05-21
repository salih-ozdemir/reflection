package tr.com.sozdemir.design2;

// Özel Part Implementasyonları
class CustomHeaderPart extends MessagePart {
    private int h1;

    public CustomHeaderPart(int h1, BaseReqRes part) {
        super(part);
        this.h1 = h1;
    }

    @Override
    public byte[] toBytes() {
        byte[] partBytes = part.toBytes();
        byte[] bytes = new byte[partBytes.length + 4];
        System.arraycopy(part.toBytes(), 0, bytes, 0, partBytes.length);
        System.arraycopy(intToBytes(h1), 0, bytes, partBytes.length, 4);
        return bytes;
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