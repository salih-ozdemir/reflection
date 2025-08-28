package heartbeat.netty;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

/**
 * Robust UDP Client with connection monitoring and automatic reconnection
 */
public class RobustUDPClient {
    private static final Logger logger = Logger.getLogger(RobustUDPClient.class.getName());

    // Connection configuration
    private final String serverHost;
    private final int serverPort;
    private final int localPort;

    // Timeout and retry settings
    private int connectionTimeout = 5000; // 5 seconds
    private int heartbeatInterval = 10000; // 10 seconds
    private int maxReconnectAttempts = 5;
    private int reconnectDelay = 2000; // 2 seconds
    private int socketTimeout = 3000; // 3 seconds

    // Connection state
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);

    // Threading
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutor;
    private Future<?> heartbeatTask;
    private Future<?> reconnectTask;

    // Heartbeat configuration
    private static final String HEARTBEAT_MESSAGE = "PING";
    private static final String HEARTBEAT_RESPONSE = "PONG";
    private long lastHeartbeatResponse = 0;

    public RobustUDPClient(String serverHost, int serverPort, int localPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localPort = localPort;

        setupLogging();
        initializeExecutors();
    }

    /**
     * Configure timeout and retry settings
     */
    public RobustUDPClient setConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
        return this;
    }

    public RobustUDPClient setHeartbeatInterval(int interval) {
        this.heartbeatInterval = interval;
        return this;
    }

    public RobustUDPClient setMaxReconnectAttempts(int attempts) {
        this.maxReconnectAttempts = attempts;
        return this;
    }

    public RobustUDPClient setReconnectDelay(int delay) {
        this.reconnectDelay = delay;
        return this;
    }

    public RobustUDPClient setSocketTimeout(int timeout) {
        this.socketTimeout = timeout;
        return this;
    }

    /**
     * Initialize the client and establish connection
     */
    public boolean connect() {
        logger.info("Attempting to connect to " + serverHost + ":" + serverPort);

        try {
            // Resolve server address
            serverAddress = InetAddress.getByName(serverHost);

            // Create and configure socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            socket = new DatagramSocket(localPort);
            socket.setSoTimeout(socketTimeout);

            // Test initial connection with ping
            if (sendHeartbeat()) {
                isConnected.set(true);
                reconnectCount.set(0);
                startHeartbeatMonitoring();
                logger.info("Successfully connected to server");
                return true;
            } else {
                logger.warning("Failed to establish initial connection");
                cleanup();
                return false;
            }

        } catch (Exception e) {
            logger.severe("Error during connection: " + e.getMessage());
            cleanup();
            return false;
        }
    }

    /**
     * Send a message to the server
     */
    public boolean sendMessage(String message) {
        if (!isConnected.get()) {
            logger.warning("Cannot send message: not connected");
            return false;
        }

        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, serverAddress, serverPort);

            socket.send(packet);
            logger.fine("Message sent: " + message);
            return true;

        } catch (Exception e) {
            logger.warning("Failed to send message: " + e.getMessage());
            handleConnectionLoss();
            return false;
        }
    }

    /**
     * Receive a message from the server
     */
    public String receiveMessage() throws IOException {
        if (!isConnected.get()) {
            throw new IOException("Not connected to server");
        }

        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            logger.fine("Message received: " + message);
            return message;

        } catch (SocketTimeoutException e) {
            // This is expected behavior for non-blocking receive
            return null;
        } catch (IOException e) {
            logger.warning("Error receiving message: " + e.getMessage());
            handleConnectionLoss();
            throw e;
        }
    }

    /**
     * Send heartbeat and wait for response
     */
    private boolean sendHeartbeat() {
        try {
            byte[] pingData = HEARTBEAT_MESSAGE.getBytes();
            DatagramPacket pingPacket = new DatagramPacket(
                    pingData, pingData.length, serverAddress, serverPort);

            socket.send(pingPacket);

            // Wait for PONG response
            byte[] buffer = new byte[64];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

            long startTime = System.currentTimeMillis();
            socket.receive(responsePacket);

            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (HEARTBEAT_RESPONSE.equals(response.trim())) {
                lastHeartbeatResponse = System.currentTimeMillis();
                logger.fine("Heartbeat successful (RTT: " + (lastHeartbeatResponse - startTime) + "ms)");
                return true;
            } else {
                logger.warning("Invalid heartbeat response: " + response);
                return false;
            }

        } catch (Exception e) {
            logger.warning("Heartbeat failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Start heartbeat monitoring task
     */
    private void startHeartbeatMonitoring() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(true);
        }

        heartbeatTask = scheduledExecutor.scheduleWithFixedDelay(() -> {
            if (!shouldRun.get()) return;

            if (!sendHeartbeat()) {
                logger.warning("Heartbeat failed - connection may be lost");
                handleConnectionLoss();
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);

        logger.info("Heartbeat monitoring started (interval: " + heartbeatInterval + "ms)");
    }

    /**
     * Handle connection loss and trigger reconnection
     */
    private void handleConnectionLoss() {
        if (!isConnected.get()) {
            return; // Already handling connection loss
        }

        logger.warning("Connection lost - initiating reconnection procedure");
        isConnected.set(false);

        // Cancel heartbeat monitoring
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }

        // Start reconnection task
        startReconnectionTask();
    }

    /**
     * Start automatic reconnection task
     */
    private void startReconnectionTask() {
        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            return; // Reconnection already in progress
        }

        reconnectTask = executorService.submit(() -> {
            int attempts = 0;

            while (shouldRun.get() && attempts < maxReconnectAttempts && !isConnected.get()) {
                attempts++;
                logger.info("Reconnection attempt " + attempts + "/" + maxReconnectAttempts);

                try {
                    Thread.sleep(reconnectDelay);

                    if (connect()) {
                        logger.info("Reconnection successful after " + attempts + " attempts");
                        reconnectCount.incrementAndGet();
                        return;
                    }

                } catch (InterruptedException e) {
                    logger.info("Reconnection task interrupted");
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    logger.warning("Reconnection attempt failed: " + e.getMessage());
                }
            }

            if (!isConnected.get() && shouldRun.get()) {
                logger.severe("Failed to reconnect after " + maxReconnectAttempts + " attempts");
            }
        });
    }

    /**
     * Get connection status
     */
    public boolean isConnected() {
        return isConnected.get();
    }

    /**
     * Get reconnection count
     */
    public int getReconnectionCount() {
        return reconnectCount.get();
    }

    /**
     * Get time since last successful heartbeat
     */
    public long getTimeSinceLastHeartbeat() {
        return lastHeartbeatResponse > 0 ? System.currentTimeMillis() - lastHeartbeatResponse : -1;
    }

    /**
     * Clean shutdown of the client
     */
    public void shutdown() {
        logger.info("Shutting down UDP client...");
        shouldRun.set(false);
        isConnected.set(false);

        // Cancel all tasks
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
        if (reconnectTask != null) {
            reconnectTask.cancel(true);
        }

        // Shutdown executors
        shutdownExecutor(scheduledExecutor, "ScheduledExecutor");
        shutdownExecutor(executorService, "ExecutorService");

        cleanup();
        logger.info("UDP client shutdown complete");
    }

    private void cleanup() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warning(name + " did not terminate gracefully, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warning("Interrupted while waiting for " + name + " termination");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void initializeExecutors() {
        executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "UDP-Client-Worker");
            t.setDaemon(true);
            return t;
        });

        scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "UDP-Client-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    private void setupLogging() {
        logger.setLevel(Level.INFO);

        // Remove default handlers
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }

        // Add console handler with custom formatter
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS] [%2$s] %3$s%n",
                        record.getMillis(), record.getLevel(), record.getMessage());
            }
        });
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false);
    }

    /**
     * Example usage and testing
     */
    public static void main(String[] args) {
        // Create UDP client
        RobustUDPClient client = new RobustUDPClient("localhost", 8888, 0)
                .setConnectionTimeout(5000)
                .setHeartbeatInterval(5000)
                .setMaxReconnectAttempts(3)
                .setReconnectDelay(2000)
                .setSocketTimeout(3000);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received");
            client.shutdown();
        }));

        try {
            // Connect to server
            if (client.connect()) {
                System.out.println("Connected to server successfully!");

                // Send some test messages
                client.sendMessage("Hello Server!");
                client.sendMessage("This is a test message");

                // Simulate running for a while to test heartbeat
                Thread.sleep(30000);

                // Send more messages
                for (int i = 0; i < 5; i++) {
                    client.sendMessage("Message " + (i + 1));
                    Thread.sleep(1000);
                }

                // Monitor connection status
                System.out.println("Connection status: " + client.isConnected());
                System.out.println("Reconnection count: " + client.getReconnectionCount());
                System.out.println("Time since last heartbeat: " + client.getTimeSinceLastHeartbeat() + "ms");

            } else {
                System.out.println("Failed to connect to server");
            }

        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted");
            Thread.currentThread().interrupt();
        } finally {
            client.shutdown();
        }
    }
}