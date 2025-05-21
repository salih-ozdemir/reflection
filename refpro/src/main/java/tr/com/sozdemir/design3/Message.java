package tr.com.sozdemir.design3;

public abstract class Message<T extends Body> {
    protected Header header;
    protected T body;
    protected Footer footer;

    public Message(Header header, T body) {
        this.header = header;
        this.body = body;
        this.footer = new Footer(body.toString()); // ya da serialize edilmiş hali
    }

    public Header getHeader() { return header; }
    public T getBody() { return body; }
    public Footer getFooter() { return footer; }
}