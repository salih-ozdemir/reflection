package tr.com.sozdemir.design2;

// BaseReqRes hiyerarşisi
class BaseReqRes {
    private int type;

    public BaseReqRes(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public byte[] toBytes() {
        return String.valueOf(type).getBytes();
    }
}