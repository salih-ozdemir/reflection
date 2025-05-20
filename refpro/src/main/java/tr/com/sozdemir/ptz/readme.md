Anladım, her komut için özel Request ve Response sınıfları oluşturmak istiyorsunuz ve bu sınıfları merkezi bir yapıda yönetmek istiyorsunuz. Bunun için bir Command Registry tasarımı önereyim:

1. Temel Yapılar
   java
   // Komut enum'ı
   public enum CommandType {
   TO_LEFT,
   TO_RIGHT,
   ZOOM_IN,
   ZOOM_OUT
   // Diğer komutlar...
   }

// Temel Request arayüzü
public interface Request {
CommandType getCommandType();
byte[] toBytes();
}

// Temel Response arayüzü
public interface Response {
CommandType getCommandType();
static Response fromBytes(byte[] bytes);
}
2. Komut Kayıt Sistemi (Registry)
   java
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
3. Örnek Komut Implementasyonları
   java
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
4. Registry Initialization
   java
   public class AppConfig {
   public static void initializeCommands() {
   CommandRegistry.register(
   CommandType.TO_LEFT,
   LeftRequest::new,
   LeftResponse::new
   );

        CommandRegistry.register(
            CommandType.TO_RIGHT,
            RightRequest::new,
            RightResponse::new
        );

        // Diğer komutlar...
   }
   }
5. Kullanım Örneği
   java
   public class PTZController {
   private final Connection connection;

   public PTZController(Connection connection) {
   this.connection = connection;
   AppConfig.initializeCommands(); // Komutları kaydet
   }

   public Response sendCommand(CommandType type) {
   // Request oluştur
   Request request = CommandRegistry.createRequest(type);

        // Gönder
        byte[] responseBytes = connection.send(request.toBytes());
        
        // Response oluştur
        return CommandRegistry.parseResponse(type, responseBytes);
   }

   public static void main(String[] args) {
   Connection conn = new Connection();
   PTZController controller = new PTZController(conn);

        // Komut gönder
        LeftResponse response = (LeftResponse) controller.sendCommand(CommandType.TO_LEFT);
        
        if (response.isSuccess()) {
            System.out.println("Left command successful");
        }
   }
   }
   Avantajlar:
   Merkezi Yönetim: Tüm komutlar tek bir noktadan yönetilir

Genişletilebilirlik: Yeni komut eklemek çok kolay

Tip Güvenliği: Her komut kendi Request/Response çiftine sahip

Otomatik Çözümleme: Gelen byte dizileri otomatik olarak doğru Response sınıfına dönüştürülür

Geliştirme Önerileri:
Annotation Tabanlı Kayıt: Komut sınıflarını @Command annotation'ı ile işaretleyerek otomatik kayıt yapabilirsiniz

Dependency Injection: Registry'yi Spring veya başka bir DI framework ile entegre edebilirsiniz

Protokol Desteği: Farklı kamera protokolleri için farklı registry'ler oluşturabilirsiniz

Bu yapı sayesinde her yeni komut eklediğinizde sadece yeni Request/Response sınıfları oluşturup registry'e kaydetmeniz yeterli olacaktır.