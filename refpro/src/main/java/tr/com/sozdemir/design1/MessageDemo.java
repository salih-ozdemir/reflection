package tr.com.sozdemir.design1;

public class MessageDemo {
    public static void main(String[] args) {
        try {
            // TO_LEFT Request oluşturma
            Header toLeftHeader = new Header("TO_LEFT", 123);
            ToLeftRequestBody toLeftBody = new ToLeftRequestBody(100, 50);
            RequestMessage toLeftRequest = new RequestMessage(toLeftHeader, toLeftBody);

            // TO_LEFT Response oluşturma
            Header responseHeader = new Header("TO_LEFT_RESPONSE", 123);
            ToLeftResponseBody responseBody = new ToLeftResponseBody(true, "Movement completed");
            ResponseMessage toLeftResponse = new ResponseMessage(responseHeader, responseBody, 200);

            // Mesajları doğrulama
            toLeftRequest.validate();
            toLeftResponse.validate();

            System.out.println("TO_LEFT Request CRC: " + toLeftRequest.getFooter().getCrc());
            System.out.println("TO_LEFT Response status: " + toLeftResponse.getStatusCode());

            // TO_RIGHT Request oluşturma
            Header toRightHeader = new Header("TO_RIGHT", 124);
            ToRightRequestBody toRightBody = new ToRightRequestBody(90, true);
            RequestMessage toRightRequest = new RequestMessage(toRightHeader, toRightBody);

            System.out.println("TO_RIGHT Request bytes length: " + toRightRequest.toBytes().length);

        } catch (InvalidMessageException e) {
            System.err.println("Message validation failed: " + e.getMessage());
        }
    }
}