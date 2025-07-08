package ortak;

public interface ManagedConnection {
    String getIdentifier(); // e.g., "localhost:50051" for gRPC, "eth0" for Ethernet
    boolean isConnected();
    void connect() throws ConnectionException; // Throws if connection fails
    void disconnect();
    // Potentially add a listener registration for state changes
    // void addConnectionListener(ConnectionListener listener);
}
