package tr.com.sozdemir.design1;

// Response mesajı
class ResponseMessage extends BaseMessage {
    private final int statusCode;

    public ResponseMessage(Header header, Body body, int statusCode) {
        super(header, body);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}