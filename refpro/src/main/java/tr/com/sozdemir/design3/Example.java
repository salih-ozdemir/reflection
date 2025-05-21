package tr.com.sozdemir.design3;

public class Example {
    public static void main(String[] args) {
        Header header = new Header();
        header.setMessageType("TO_LEFT");
        header.setTimestamp("2025-05-22T12:00:00Z");

        ToLeftRequestBody body = new ToLeftRequestBody(45);

        RequestMessage<ToLeftRequestBody> message = new RequestMessage<>(header, body);

// Gönder veya işleme al
    }
}
