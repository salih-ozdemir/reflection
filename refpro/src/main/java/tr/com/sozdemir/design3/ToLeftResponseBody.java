package tr.com.sozdemir.design3;

public class ToLeftResponseBody extends BaseMessageBody {
    private boolean success;

    public ToLeftResponseBody(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() { return success; }
}