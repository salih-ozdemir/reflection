package tr.com.sozdemir.design1;

// Temel Request/Response sınıfı
abstract class BaseMessage {
    protected Header header;
    protected Body body;
    protected Footer footer;

    public BaseMessage(Header header, Body body) {
        this.header = header;
        this.body = body;
        this.footer = new Footer(calculateCrc());
    }

    private long calculateCrc() {
        CRC32 crc = new CRC32();
        crc.update(header.toBytes());
        crc.update(body.toBytes());
        return crc.getValue();
    }

    public Header getHeader() {
        return header;
    }

    public Body getBody() {
        return body;
    }

    public Footer getFooter() {
        return footer;
    }

    public byte[] toBytes() {
        byte[] messageBytes = new byte[header.toBytes().length + body.toBytes().length + footer.toBytes().length];
        System.arraycopy(header.toBytes(), 0, messageBytes, 0, header.toBytes().length);
        System.arraycopy(body.toBytes(), 0, messageBytes, header.toBytes().length, body.toBytes().length);
        System.arraycopy(footer.toBytes(), 0, messageBytes,
                header.toBytes().length + body.toBytes().length, footer.toBytes().length);
        return messageBytes;
    }

    public void validate() throws InvalidMessageException {
        header.validate();
        body.validate();
        footer.validate();

        if (footer.getCrc() != calculateCrc()) {
            throw new InvalidMessageException("CRC validation failed");
        }
    }
}