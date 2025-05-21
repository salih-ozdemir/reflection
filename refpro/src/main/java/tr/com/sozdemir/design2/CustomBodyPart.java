package tr.com.sozdemir.design2;


class CustomBodyPart extends MessagePart {
    private int b1;

    public CustomBodyPart(int b1, BaseReqRes part) {
        super(part);
        this.b1 = b1;
    }

    @Override
    public byte[] toBytes() {
        byte[] partBytes = part.toBytes();
        byte[] bytes = new byte[partBytes.length + 4];
        System.arraycopy(part.toBytes(), 0, bytes, 0, partBytes.length);
        System.arraycopy(intToBytes(b1), 0, bytes, partBytes.length, 4);
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