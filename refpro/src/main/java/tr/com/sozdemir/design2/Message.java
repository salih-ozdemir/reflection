package tr.com.sozdemir.design2;

import java.util.zip.CRC32;

// Temel Mesaj Yapısı
class Message {
    private MessagePart header;
    private MessagePart body;
    private MessagePart footer;

    public Message(MessagePart header, MessagePart body) {
        this.header = header;
        this.body = body;
        this.footer = createFooter();
    }

    private MessagePart createFooter() {
        CRC32 crc = new CRC32();
        crc.update(header.toBytes());
        crc.update(body.toBytes());
        return new FooterPart((int)crc.getValue());
    }

    // Getter metodları
    public MessagePart getHeader() { return header; }
    public MessagePart getBody() { return body; }
    public MessagePart getFooter() { return footer; }

    public byte[] toBytes() {
        byte[] bytes = new byte[header.toBytes().length + body.toBytes().length + footer.toBytes().length];
        System.arraycopy(header.toBytes(), 0, bytes, 0, header.toBytes().length);
        System.arraycopy(body.toBytes(), 0, bytes, header.toBytes().length, body.toBytes().length);
        System.arraycopy(footer.toBytes(), 0, bytes,
                header.toBytes().length + body.toBytes().length, footer.toBytes().length);
        return bytes;
    }
}