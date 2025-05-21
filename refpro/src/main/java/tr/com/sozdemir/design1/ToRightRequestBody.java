package tr.com.sozdemir.design1;


// TO_RIGHT için Request Body
class ToRightRequestBody extends Body {
    private final int angle;
    private final boolean immediate;

    public ToRightRequestBody(int angle, boolean immediate) {
        this.angle = angle;
        this.immediate = immediate;
    }

    public int getAngle() {
        return angle;
    }

    public boolean isImmediate() {
        return immediate;
    }

    @Override
    public byte[] toBytes() {
        return ("TO_RIGHT|" + angle + "|" + immediate).getBytes();
    }

    @Override
    public void validate() throws InvalidMessageException {
        if (angle < 0 || angle > 360) {
            throw new InvalidMessageException("Angle must be between 0 and 360");
        }
    }
}