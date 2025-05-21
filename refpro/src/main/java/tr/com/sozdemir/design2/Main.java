package tr.com.sozdemir.design2;

// Kullanım Örneği
public class Main {
    public static void main(String[] args) {
        // LeftRequest oluşturma
        LeftRequest leftReq = new LeftRequest(1, 45, 100);
        CustomHeaderPart header = new CustomHeaderPart(0xABCD, leftReq);
        CustomBodyPart body = new CustomBodyPart(0x1234, leftReq);

        // Mesaj oluşturma
        Message message = new Message(header, body);

        // LeftResponse oluşturma
        LeftResponse leftRes = new LeftResponse(2, 0);
        CustomHeaderPart resHeader = new CustomHeaderPart(0xDCBA, leftRes);
        CustomBodyPart resBody = new CustomBodyPart(0x4321, leftRes);

        // Yanıt mesajı oluşturma
        Message response = new Message(resHeader, resBody);

        System.out.println("Request Message Size: " + message.toBytes().length + " bytes");
        System.out.println("Response Message Size: " + response.toBytes().length + " bytes");
    }
}