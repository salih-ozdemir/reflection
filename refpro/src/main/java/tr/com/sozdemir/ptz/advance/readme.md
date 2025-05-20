Geliştirme Önerileri ile Genişletilmiş PTZ Kamera Haberleşme Sistemi
İşte önceki Command Registry yapısını geliştirme önerileriyle genişlettiğim örnekler:

1. Annotation Tabanlı Otomatik Kayıt Sistemi
   java
   // Command işaretleme annotation'ı
   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.TYPE)
   public @interface PTZCommand {
   CommandType value();
   }

// Annotation işlemci sınıfı
public class CommandScanner {
public static void scanAndRegister(String basePackage) {
Reflections reflections = new Reflections(basePackage);

        // Request sınıflarını bul
        Set<Class<?>> requestClasses = reflections.getTypesAnnotatedWith(PTZCommand.class);
        
        for (Class<?> clazz : requestClasses) {
            if (Request.class.isAssignableFrom(clazz)) {
                PTZCommand annotation = clazz.getAnnotation(PTZCommand.class);
                CommandType commandType = annotation.value();
                
                // İlgili Response sınıfını bul (Request sınıf adından "Request" kısmını "Response" ile değiştirerek)
                String responseClassName = clazz.getName().replace("Request", "Response");
                try {
                    Class<?> responseClass = Class.forName(responseClassName);
                    
                    // Registry'e kaydet
                    CommandRegistry.register(
                        commandType,
                        (Supplier<Request>) () -> {
                            try {
                                return (Request) clazz.getDeclaredConstructor().newInstance();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        (Supplier<Response>) () -> {
                            try {
                                return (Response) responseClass.getDeclaredConstructor().newInstance();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    );
                } catch (ClassNotFoundException e) {
                    System.err.println("Response sınıfı bulunamadı: " + responseClassName);
                }
            }
        }
    }
}

// Kullanım örneği:
@PTZCommand(CommandType.TO_LEFT)
public class LeftRequest implements Request {
// implementasyon
}

public class LeftResponse implements Response {
// implementasyon
}

// Uygulama başlangıcında:
CommandScanner.scanAndRegister("com.ptz.commands");
2. Spring Entegrasyonu
   java
   @Configuration
   public class PTZCommandConfig {

   @Bean
   public CommandRegistry commandRegistry() {
   CommandRegistry registry = new CommandRegistry();

        registry.register(
            CommandType.TO_LEFT,
            () -> new LeftRequest(),
            () -> new LeftResponse()
        );
        
        // Diğer komutlar...
        
        return registry;
   }

   @Bean
   @ConditionalOnMissingBean
   public PTZController ptzController(CommandRegistry registry, Connection connection) {
   return new PTZController(registry, connection);
   }
   }

// Spring Boot uygulamasında otomatik taranacak paket
@SpringBootApplication
@ComponentScan("com.ptz.commands")
public class PTZApplication {
public static void main(String[] args) {
SpringApplication.run(PTZApplication.class, args);
}
}
3. Protokol Desteği için Strategy Pattern
   java
   // Protokol arayüzü
   public interface PTZProtocol {
   byte[] encodeRequest(Request request);
   Response decodeResponse(CommandType type, byte[] data);
   }

// Pelco-D protokolü implementasyonu
public class PelcoDProtocol implements PTZProtocol {
@Override
public byte[] encodeRequest(Request request) {
// Pelco-D özelinde request'i byte dizisine çevir
// Örnek: 0xFF, 0x01, 0x00, 0x04, 0x00, 0x00, 0x05, 0xFF
return new byte[]{(byte) 0xFF, 0x01, 0x00, 0x04, 0x00, 0x00, 0x05, (byte) 0xFF};
}

    @Override
    public Response decodeResponse(CommandType type, byte[] data) {
        // Pelco-D yanıtını parse et
        return CommandRegistry.createResponse(type);
    }
}

// ONVIF protokolü implementasyonu
public class OnvifProtocol implements PTZProtocol {
@Override
public byte[] encodeRequest(Request request) {
// ONVIF SOAP mesajı oluştur
return ("<SOAP-ENV:Envelope>" +
"<SOAP-ENV:Body>" +
"<tptz:Left/>" +
"</SOAP-ENV:Body>" +
"</SOAP-ENV:Envelope>").getBytes();
}

    @Override
    public Response decodeResponse(CommandType type, byte[] data) {
        // SOAP yanıtını parse et
        return CommandRegistry.createResponse(type);
    }
}

// Protokol registry'si
public class ProtocolRegistry {
private static final Map<String, PTZProtocol> protocols = new HashMap<>();

    static {
        register("pelco-d", new PelcoDProtocol());
        register("onvif", new OnvifProtocol());
    }
    
    public static void register(String name, PTZProtocol protocol) {
        protocols.put(name, protocol);
    }
    
    public static PTZProtocol getProtocol(String name) {
        return protocols.get(name);
    }
}

// Connection sınıfını protokol desteği ile güncelleme
public class ProtocolAwareConnection {
private final PTZProtocol protocol;

    public ProtocolAwareConnection(String protocolName) {
        this.protocol = ProtocolRegistry.getProtocol(protocolName);
    }
    
    public Response sendCommand(Request request) {
        byte[] requestData = protocol.encodeRequest(request);
        byte[] responseData = sendOverNetwork(requestData);
        return protocol.decodeResponse(request.getCommandType(), responseData);
    }
    
    private byte[] sendOverNetwork(byte[] data) {
        // Gerçek ağ iletişimi
        return new byte[0];
    }
}
4. Dinamik Komut Yükleyici
   java
   public class DynamicCommandLoader {
   private final String commandsDir;

   public DynamicCommandLoader(String commandsDir) {
   this.commandsDir = commandsDir;
   }

   public void loadNewCommands() {
   try {
   // JAR dosyalarını tara
   Files.list(Paths.get(commandsDir))
   .filter(path -> path.toString().endsWith(".jar"))
   .forEach(this::loadJar);
   } catch (IOException e) {
   e.printStackTrace();
   }
   }

   private void loadJar(Path jarPath) {
   try (URLClassLoader loader = new URLClassLoader(
   new URL[]{jarPath.toUri().toURL()},
   this.getClass().getClassLoader())) {

            // JAR içindeki sınıfları tara
            JarFile jarFile = new JarFile(jarPath.toFile());
            Enumeration<JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                        .replace("/", ".")
                        .replace(".class", "");
                    
                    try {
                        Class<?> clazz = loader.loadClass(className);
                        if (clazz.isAnnotationPresent(PTZCommand.class)) {
                            registerCommandClass(clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
   }

   private void registerCommandClass(Class<?> clazz) {
   // Annotation'dan komut tipini al
   PTZCommand annotation = clazz.getAnnotation(PTZCommand.class);
   CommandType commandType = annotation.value();

        // Sınıfın Request mi Response mu olduğunu belirle
        if (Request.class.isAssignableFrom(clazz)) {
            // Request sınıfını kaydet
            registerRequestClass(commandType, clazz);
        } else if (Response.class.isAssignableFrom(clazz)) {
            // Response sınıfını kaydet
            registerResponseClass(commandType, clazz);
        }
   }

   private void registerRequestClass(CommandType type, Class<?> clazz) {
   // Registry'e request sınıfını ekle
   // (Önceki kayıtları güncelleme mantığı eklenebilir)
   }

   private void registerResponseClass(CommandType type, Class<?> clazz) {
   // Registry'e response sınıfını ekle
   }
   }
5. Komut Zinciri (Chain of Responsibility) ile İşlem Hattı
   java
   // İşlem adımı arayüzü
   public interface CommandProcessingStep {
   void process(Request request, Response response, CommandProcessingChain chain);
   }

// İşlem zinciri
public class CommandProcessingChain {
private final List<CommandProcessingStep> steps;
private int index = 0;

    public CommandProcessingChain(List<CommandProcessingStep> steps) {
        this.steps = steps;
    }
    
    public void proceed(Request request, Response response) {
        if (index < steps.size()) {
            CommandProcessingStep step = steps.get(index++);
            step.process(request, response, this);
        }
    }
}

// Örnek işlem adımları
public class LoggingStep implements CommandProcessingStep {
@Override
public void process(Request request, Response response, CommandProcessingChain chain) {
System.out.println("Processing command: " + request.getCommandType());
chain.proceed(request, response);
}
}

public class ValidationStep implements CommandProcessingStep {
@Override
public void process(Request request, Response response, CommandProcessingChain chain) {
if (request == null) {
throw new IllegalArgumentException("Request cannot be null");
}
chain.proceed(request, response);
}
}

// Zincir kullanımı
public class PTZController {
private final CommandProcessingChain processingChain;

    public PTZController() {
        this.processingChain = new CommandProcessingChain(Arrays.asList(
            new LoggingStep(),
            new ValidationStep(),
            // Diğer adımlar...
        ));
    }
    
    public Response executeCommand(Request request) {
        Response response = new EmptyResponse(request.getCommandType());
        processingChain.proceed(request, response);
        return response;
    }
}
Bu geliştirmelerle birlikte sisteminiz:

Annotation tabanlı otomatik kayıt sayesinde yeni komut eklemek çok kolay hale gelir

Spring entegrasyonu ile dependency management sağlanır

Çoklu protokol desteği ile farklı kamera markalarına uyum sağlar

Dinamik yükleme ile çalışma zamanında yeni komutlar eklenebilir

İşlem zinciri ile merkezi kontrol ve genişletilebilirlik sağlanır

Her bir geliştirme önerisini proje ihtiyaçlarınıza göre ayrı ayrı veya birlikte uygulayabilirsiniz.