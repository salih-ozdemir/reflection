package tr.com.sozdemir.ptz;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class CommandRegistry {
    private static final Map<CommandType, CommandPair<?>> registry = new HashMap<>();

    // Her komut tipi için Request ve Response supplier'larını tutan kayıt
    private static class CommandPair<T extends Response> {
        final Supplier<? extends Request> requestSupplier;
        final Supplier<T> responseSupplier;

        CommandPair(Supplier<? extends Request> requestSupplier, Supplier<T> responseSupplier) {
            this.requestSupplier = requestSupplier;
            this.responseSupplier = responseSupplier;
        }
    }

    // Komut kaydı ekleme
    public static <T extends Response> void register(
            CommandType type,
            Supplier<? extends Request> requestSupplier,
            Supplier<T> responseSupplier) {
        registry.put(type, new CommandPair<>(requestSupplier, responseSupplier));
    }

    // Request oluşturma
    public static Request createRequest(CommandType type) {
        CommandPair<?> pair = registry.get(type);
        if (pair == null) {
            throw new IllegalArgumentException("Unregistered command type: " + type);
        }
        return pair.requestSupplier.get();
    }

    // Response oluşturma
    public static Response createResponse(CommandType type) {
        CommandPair<?> pair = registry.get(type);
        if (pair == null) {
            throw new IllegalArgumentException("Unregistered command type: " + type);
        }
        return pair.responseSupplier.get();
    }

    // Response'u byte[]'den oluşturma
    public static Response parseResponse(CommandType type, byte[] bytes) {
        Response response = createResponse(type);
        return response.fromBytes(bytes);
    }
}