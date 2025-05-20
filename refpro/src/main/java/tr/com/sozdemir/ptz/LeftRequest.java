package tr.com.sozdemir.ptz;

// TO_LEFT komutu için Request
public class LeftRequest implements Request {
    @Override
    public CommandType getCommandType() {
        return CommandType.TO_LEFT;
    }

    @Override
    public byte[] toBytes() {
        return "LEFT_REQUEST".getBytes();
    }
}

// TO_LEFT komutu için Response
public class LeftResponse implements Response {
    private boolean success;

    @Override
    public CommandType getCommandType() {
        return CommandType.TO_LEFT;
    }

    public static LeftResponse fromBytes(byte[] bytes) {
        LeftResponse response = new LeftResponse();
        response.success = new String(bytes).contains("OK");
        return response;
    }

    public boolean isSuccess() {
        return success;
    }
}