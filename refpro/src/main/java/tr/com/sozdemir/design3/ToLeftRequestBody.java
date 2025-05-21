package tr.com.sozdemir.design3;

public class ToLeftRequestBody extends BaseMessageBody {
    private int degrees;

    public ToLeftRequestBody(int degrees) {
        this.degrees = degrees;
    }

    public int getDegrees() { return degrees; }
}