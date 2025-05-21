package tr.com.sozdemir.design1;

// TO_LEFT için Request Body
class ToLeftRequestBody extends Body {
    private final int distance;
    private final int speed;

    public ToLeftRequestBody(int distance, int speed) {
        this.distance = distance;
        this.speed = speed;
    }

    public int getDistance() {
        return distance;
    }

    public int getSpeed() {
        return speed;
    }

    @Override
    public byte[] toBytes() {
        return ("TO_LEFT|" + distance + "|" + speed).getBytes();
    }

    @Override
    public void validate() throws InvalidMessageException {
        if (distance <= 0) {
            throw new InvalidMessageException("Distance must be positive");
        }
        if (speed <= 0) {
            throw new InvalidMessageException("Speed must be positive");
        }
    }
}
