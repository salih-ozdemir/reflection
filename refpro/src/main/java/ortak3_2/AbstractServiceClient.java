package ortak3_2;

/**
 * Marker interface for all service clients managed by the UniversalConnectionManager.
 * This allows the manager to return a generic client type without knowing the specific protocol.
 */
public interface AbstractServiceClient {
    // This interface might be empty or contain common methods like 'healthCheck()' if applicable
}