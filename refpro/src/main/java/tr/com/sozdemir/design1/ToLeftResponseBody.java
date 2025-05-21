package tr.com.sozdemir.design1;

// TO_LEFT için Response Body
class ToLeftResponseBody extends Body {
    private final boolean success;
    private final String message;

    public ToLeftResponseBody(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public byte[] toBytes() {
        return (success + "|" + message).getBytes();
    }

    @Override
    public void validate() throws InvalidMessageException {
        if (message == null) {
            throw new InvalidMessageException("Message cannot be null");
        }
    }
}