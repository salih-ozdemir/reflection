package heartbeat.reactive;

public class JoystickCommandExecutor {
    private final BlockingQueue<Command> queue = new LinkedBlockingQueue<>();
    private final UdpConnection udp;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public JoystickCommandExecutor(UdpConnection udp) {
        this.udp = udp;
        startWorker();
    }

    public void enqueue(Command cmd) {
        // aynı tip komutları drop et
        queue.removeIf(c -> c.getType().equals(cmd.getType()));
        queue.offer(cmd);
    }

    private void startWorker() {
        scheduler.scheduleAtFixedRate(() -> {
            Command cmd = queue.poll(); // non-blocking al
            if (cmd != null) {
                udp.send(cmd.toBytes());
            }
        }, 0, 50, TimeUnit.MILLISECONDS); // her 50ms’de bir çalışır
    }
}