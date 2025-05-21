package tr.com.sozdemir.design3;

public class RequestMessage<T extends BaseMessageBody> extends Message<T> {
    public RequestMessage(Header header, T body) {
        super(header, body);
    }
}