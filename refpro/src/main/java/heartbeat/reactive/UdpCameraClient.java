package heartbeat.reactive;

public class UdpCameraClient {
    private final UdpConnection udp;
    private final PendingRequestManager pending = new PendingRequestManager();
    private final JoystickCommandExecutor joystickExecutor;

    public UdpCameraClient(UdpConnection udp) {
        this.udp = udp;
        this.joystickExecutor = new JoystickCommandExecutor(udp);
        this.udp.startListening(this::onMessage);
    }

    public CompletableFuture<Response> sendCommand(Command cmd) {
        CompletableFuture<Response> future = pending.register(cmd.getType());
        udp.send(cmd.toBytes());

        future.orTimeout(2, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    System.err.println("Timeout: " + cmd);
                    return null;
                });

        return future;
    }

    public void sendJoystickCommand(Command cmd) {
        joystickExecutor.enqueue(cmd);
    }

    private void onMessage(byte[] msg) {
        Response res = Response.fromBytes(msg);
        pending.complete(res.getCommandType(), res);
    }
}