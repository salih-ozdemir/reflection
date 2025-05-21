package tr.com.sozdemir.design2;

class LeftResponse extends BaseReqRes {
    private int result;

    public LeftResponse(int type, int result) {
        super(type);
        this.result = result;
    }

    @Override
    public byte[] toBytes() {
        byte[] bytes = new byte[8]; // type(4) + result(4)
        System.arraycopy(super.toBytes(), 0, bytes, 0, 4);
        System.arraycopy(intToBytes(result), 0, bytes, 4, 4);
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