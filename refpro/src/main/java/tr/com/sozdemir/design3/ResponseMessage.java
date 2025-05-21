package tr.com.sozdemir.design3;

public class ResponseMessage<T extends BaseMessageBody> extends Message<T> {
    public ResponseMessage(Header header, T body) {
        super(header, body);
    }
}