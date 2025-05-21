package tr.com.sozdemir.design2;

// Request/Response Implementasyonları
class LeftRequest extends BaseReqRes {
    private int angle;
    private int line;

    public LeftRequest(int type, int angle, int line) {
        super(type);
        this.angle = angle;
        this.line = line;
    }

    @Override
    public byte[] toBytes() {
        byte[] bytes = new byte[12]; // type(4) + angle(4) + line(4)
        System.arraycopy(super.toBytes(), 0, bytes, 0, 4);
        System.arraycopy(intToBytes(angle), 0, bytes, 4, 4);
        System.arraycopy(intToBytes(line), 0, bytes, 8, 4);
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