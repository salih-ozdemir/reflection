package ortak3;


public interface ManagedConnection {
    String getIdentifier();
    boolean isConnected();
    void connect() throws ConnectionException;
    void disconnect();
}