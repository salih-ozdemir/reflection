package tr.com.sozdemir.design2;

// MessagePart soyut sınıfı
abstract class MessagePart {
    protected BaseReqRes part;

    public MessagePart(BaseReqRes part) {
        this.part = part;
    }

    public BaseReqRes getPart() {
        return part;
    }

    public abstract byte[] toBytes();
}
