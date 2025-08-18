Best practice bir yaklaşıma ihtiyacım var. Farklı kameralara da destek verebilmek için ui tarafında tüm komutları tek düşürdüm. Mesela sola dön tüm kameralarda To_left olacak. Ancak kamera tarafında da goto_pos şeklinde aslinda. Bunları bir yerde map yapmam mi gerekecek yada best practice ne var. Yine aynı şekilde termal veya normal kameralarda aynı komut gönderiliyor mesela set_preset gibi. Şimdi ui icersinde su gonderiliyor
{
Cameraid:10,
Command: save_preset
Params:{
Cameratype: thermal
.......
}
Şeklinde. Ben de backend de bu komudu aldığımda bu bir thermal kamera onun methodu budur. Mesajı budur call edip dönen mesaji budur şeklinde map yapmak mantıklı nasıl bir yapı onerebilirsin

ChatGPT:

Güzel soru 👍 Aslında burada iki ayrı konuyu çözmek istiyorsun:

UI tarafında komutların tekilleştirilmesi (örn. to_left, save_preset) → Kullanıcıya kamera tipinden bağımsız tek bir API sunmak.

Backend tarafında kamera tipine göre komutların karşılıklarını çözümlemek (örn. thermal kamerada save_preset aslında şu API çağrısı, ptz kamerada farklı bir API çağrısı).

Bu senaryoda best practice genelde şu şekilde kurulur:

1. Unified Command Model (Domain Command)

UI’dan gelen komutları ortak bir domain command modeli olarak düşün.
Örneğin:

{
"cameraId": 10,
"command": "SAVE_PRESET",
"params": {
"cameraType": "THERMAL",
"presetName": "Target_1"
}
}


Burada command → domain level command (tüm kameralar için tek).

2. Command → Adapter Mapping

Backend’de Command Handler veya Command Mapper yapısı kurarsın:

Gelen command + cameraType bilgisine göre doğru adapter çağrılır.

Adapter kendi içinde “kamera üreticisine / tipine özel” protokole çevirir.

Örnek Tasarım
// UI’dan gelen ortak command
class CameraCommand {
String cameraId;
String command; // e.g. SAVE_PRESET
Map<String, Object> params;
CameraType cameraType;
}

// Ortak interface
interface CameraCommandHandler {
boolean supports(CameraType type, String command);
CommandResult handle(CameraCommand cmd);
}

// Thermal kamera handler
class ThermalSavePresetHandler implements CameraCommandHandler {
@Override
public boolean supports(CameraType type, String command) {
return type == CameraType.THERMAL && "SAVE_PRESET".equals(command);
}

    @Override
    public CommandResult handle(CameraCommand cmd) {
        // thermal kameraya özgü API çağrısı
        return thermalSdk.savePreset(cmd.params.get("presetName"));
    }
}

// PTZ kamera handler
class PtzSavePresetHandler implements CameraCommandHandler {
@Override
public boolean supports(CameraType type, String command) {
return type == CameraType.PTZ && "SAVE_PRESET".equals(command);
}

    @Override
    public CommandResult handle(CameraCommand cmd) {
        return ptzSdk.storePreset(cmd.params.get("presetName"));
    }
}


Dispatcher:

class CommandDispatcher {
private final List<CameraCommandHandler> handlers;

    public CommandDispatcher(List<CameraCommandHandler> handlers) {
        this.handlers = handlers;
    }

    public CommandResult dispatch(CameraCommand cmd) {
        return handlers.stream()
                .filter(h -> h.supports(cmd.cameraType, cmd.command))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No handler found"))
                .handle(cmd);
    }
}

3. Bu Yapının Avantajları

UI tarafı hep aynı JSON gönderir → tip bağımsız.

Backend’de adapter pattern ile kamera tipine göre mapping yapılır.

Yeni kamera eklendiğinde sadece yeni handler implementasyonu eklenir (OCP – open/closed principle).

Komut mapping işi kodun içinde kontrol altında olur (hardcoded map değil, interface + implementasyon).

4. Alternatif / Daha Dinamik

Eğer komut–kamera mapping’ini runtime configurable yapmak istersen:

Komut–kamera tip–native method eşleşmelerini YAML/DB’de saklayabilirsin.

Örn:

thermal:
SAVE_PRESET: "goto_save_preset"
ptz:
SAVE_PRESET: "storePreset"


Backend’de bu tabloya bakıp doğru native çağrı yapılır.
Ama bu yaklaşım adapter pattern kadar okunabilir ve tip güvenli değildir, genelde SDK/API farkları büyük olduğundan kod tabanında handler ile çözmek daha temiz.

✅ Best practice önerim:

UI’da domain command tek olsun.

Backend’de adapter/handler pattern ile çözümle.

Eğer çok fazla kamera tipi/komut olacaksa mapping’i konfigürasyona alabilirsin ama “iş mantığı farklı” komutlarda yine handler pattern daha doğru olur.

👉 İstersen sana bunun Spring Boot + Command Dispatcher şeklinde hazır bir iskelet kod çıkarabilirim. İster configuration-driven ister adapter-driven. Hangisini tercih edersin?

Şunu dedin:
Aslindq çok fazla komut olacak 60 dan fazla
ChatGPT:

O zaman 👍 senin durumda 60+ komut olacağı için, hepsini tek tek if/else ya da ayrı ayrı handler yazmak çok zahmetli olur.
Ama aynı zamanda SDK/protokol tarafında farklılıklar da var → yani tamamen YAML ile map’lemek de gerçekçi değil, çünkü bazı komutlar farklı parametre işliyor.

Burada hibrit bir best practice yaklaşım daha doğru:

🚀 Önerilen Mimari: Command Dispatcher + Configurable Mapping
1. UI Domain Commandları Sabit Tut

UI hep şunu göndersin:

{
"cameraId": 10,
"command": "SAVE_PRESET",
"params": {
"cameraType": "THERMAL",
"presetName": "Target_1"
}
}

2. Backend’de Dispatcher

Backend’de ortak bir CommandDispatcher olur.
Bu dispatcher şuna bakar:

cameraType (ör. THERMAL, PTZ, RADAR)

command (ör. SAVE_PRESET, TO_LEFT)

Ve doğru CommandExecutor’ı çağırır.

3. Configurable Mapping Tablosu

60+ komutu kolay yönetmek için mapping’i config dosyası ya da DB’de tut:

thermal:
SAVE_PRESET: "thermalSavePresetExecutor"
TO_LEFT: "thermalMoveLeftExecutor"

ptz:
SAVE_PRESET: "ptzStorePresetExecutor"
TO_LEFT: "ptzPanLeftExecutor"


Bu tabloda:

Key → domain command (UI’dan gelen).

Value → backend’de hangi executor kullanılacak.

4. Executor Interface

Her kamera tipine özel iş mantığını kod tarafında Executor sınıflarında tutarsın:

public interface CommandExecutor {
CommandResult execute(CameraCommand cmd);
}


Örnek:

@Component("thermalSavePresetExecutor")
public class ThermalSavePresetExecutor implements CommandExecutor {
@Override
public CommandResult execute(CameraCommand cmd) {
String presetName = (String) cmd.getParams().get("presetName");
return thermalSdk.savePreset(presetName);
}
}

@Component("ptzPanLeftExecutor")
public class PtzPanLeftExecutor implements CommandExecutor {
@Override
public CommandResult execute(CameraCommand cmd) {
return ptzSdk.pan(-5); // 5 derece sola dön
}
}

5. Dispatcher Implementation

Dispatcher runtime’da mapping tablosuna bakar → hangi executor çağrılacak → Spring context’ten executor’u alır → çalıştırır.

@Component
public class CommandDispatcher {

    private final Map<String, CommandExecutor> executors;
    private final Map<String, Map<String, String>> mapping; // cameraType -> command -> beanName

    public CommandDispatcher(Map<String, CommandExecutor> executors,
                             CommandMappingProperties properties) {
        this.executors = executors;
        this.mapping = properties.getMapping();
    }

    public CommandResult dispatch(CameraCommand cmd) {
        String beanName = mapping
            .getOrDefault(cmd.getCameraType().name().toLowerCase(), Map.of())
            .get(cmd.getCommand());

        if (beanName == null) {
            throw new IllegalArgumentException("No executor found for " + cmd);
        }

        CommandExecutor executor = executors.get(beanName);
        return executor.execute(cmd);
    }
}

🎯 Bu Yapının Avantajları

✅ 60+ komut olsa bile tek tek if/else yazmana gerek yok.
✅ Yeni kamera tipi/komut eklendiğinde sadece YAML + executor sınıfı eklemen yeterli.
✅ UI hep aynı kalıyor → “komutlar tekilleşmiş domain command seti”.
✅ Backend’de SDK/protokol farklılıkları executor’larda soyutlanıyor.

👉 Yani:

Komut setini UI’da tekilleştir (domain commands).

Mapping tablosunu config/DB’de tut (kamera tipine göre komut eşleştirmesi).

İş mantığını executor’larda tut (adapter pattern).

İstersen ben sana bunun Spring Boot + YAML mapping + executor şeklinde çalışır bir mini skeleton proje çıkarabilirim. İstiyor musun?

Şunu dedin:
Evet
ChatGPT:

Süper 👍 O zaman sana Spring Boot + YAML mapping + executor tabanlı mini skeleton çıkarıyorum. Bu yapı senin 60+ komutunu yönetebilecek ve kolay genişletilebilir olacak 🚀

📂 Proje Yapısı
src/main/java/com/example/camera
├── command
│    ├── CameraCommand.java
│    ├── CommandDispatcher.java
│    ├── CommandExecutor.java
│    ├── CommandMappingProperties.java
│    └── executors
│         ├── ThermalSavePresetExecutor.java
│         └── PtzPanLeftExecutor.java
└── CameraApplication.java
src/main/resources
└── application.yml

📝 1. Domain Command
// CameraCommand.java
package com.example.camera.command;

import java.util.Map;

public class CameraCommand {
private Long cameraId;
private String command;
private Map<String, Object> params;
private String cameraType; // "THERMAL", "PTZ" ...

    // getters/setters
}

📝 2. Executor Interface
// CommandExecutor.java
package com.example.camera.command;

public interface CommandExecutor {
CommandResult execute(CameraCommand cmd);
}

// CommandResult.java
package com.example.camera.command;

public class CommandResult {
private boolean success;
private String message;

    public static CommandResult ok(String msg) {
        CommandResult r = new CommandResult();
        r.success = true;
        r.message = msg;
        return r;
    }

    public static CommandResult fail(String msg) {
        CommandResult r = new CommandResult();
        r.success = false;
        r.message = msg;
        return r;
    }

    // getters
}

📝 3. Executors (örnek)
// executors/ThermalSavePresetExecutor.java
package com.example.camera.command.executors;

import com.example.camera.command.*;
import org.springframework.stereotype.Component;

@Component("thermalSavePresetExecutor")
public class ThermalSavePresetExecutor implements CommandExecutor {
@Override
public CommandResult execute(CameraCommand cmd) {
String presetName = (String) cmd.getParams().get("presetName");
// burada thermal SDK çağrısı yapılır
return CommandResult.ok("Thermal preset saved: " + presetName);
}
}

// executors/PtzPanLeftExecutor.java
package com.example.camera.command.executors;

import com.example.camera.command.*;
import org.springframework.stereotype.Component;

@Component("ptzPanLeftExecutor")
public class PtzPanLeftExecutor implements CommandExecutor {
@Override
public CommandResult execute(CameraCommand cmd) {
// PTZ SDK çağrısı (örnek sola dön)
return CommandResult.ok("PTZ moved left 5 degrees");
}
}

📝 4. Mapping Config
// CommandMappingProperties.java
package com.example.camera.command;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "command.mapping")
public class CommandMappingProperties {
private Map<String, Map<String, String>> map;

    public Map<String, Map<String, String>> getMap() {
        return map;
    }

    public void setMap(Map<String, Map<String, String>> map) {
        this.map = map;
    }
}

📝 5. Dispatcher
// CommandDispatcher.java
package com.example.camera.command;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CommandDispatcher {

    private final Map<String, CommandExecutor> executors;
    private final CommandMappingProperties properties;

    public CommandDispatcher(Map<String, CommandExecutor> executors,
                             CommandMappingProperties properties) {
        this.executors = executors;
        this.properties = properties;
    }

    public CommandResult dispatch(CameraCommand cmd) {
        String beanName = properties.getMap()
                .getOrDefault(cmd.getCameraType().toLowerCase(), Map.of())
                .get(cmd.getCommand());

        if (beanName == null) {
            return CommandResult.fail("No executor found for command: " + cmd.getCommand());
        }

        CommandExecutor executor = executors.get(beanName);
        if (executor == null) {
            return CommandResult.fail("Executor bean not found: " + beanName);
        }

        return executor.execute(cmd);
    }
}

📝 6. application.yml
command:
mapping:
thermal:
SAVE_PRESET: "thermalSavePresetExecutor"
ptz:
TO_LEFT: "ptzPanLeftExecutor"

📝 7. Örnek Kullanım (Controller)
// CameraController.java
package com.example.camera;

import com.example.camera.command.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/camera")
public class CameraController {

    private final CommandDispatcher dispatcher;

    public CameraController(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/command")
    public CommandResult sendCommand(@RequestBody CameraCommand cmd) {
        return dispatcher.dispatch(cmd);
    }
}

🎯 Örnek Request
POST /camera/command
Content-Type: application/json

{
"cameraId": 10,
"command": "SAVE_PRESET",
"cameraType": "THERMAL",
"params": {
"presetName": "Target_1"
}
}


Response:

{
"success": true,
"message": "Thermal preset saved: Target_1"
}


✅ Bu skeleton ile:

60+ komut YAML’de yönetilebilir.

Her kamera tipine özel SDK çağrılarını Executor sınıflarında soyutluyorsun.

Yeni komut eklemek için sadece:

Executor yaz

YAML mapping ekle

İstersen ben bunun çalışır Spring Boot demo projesi şeklinde full kodunu çıkarıp sana zip verebilirim. Bunu ister misin, yoksa bu skeleton senin için yeterli mi?

Şunu dedin:
Bunu yamlveya DB disinda yapmak istesem
ChatGPT:

Çok güzel bir nokta 👍

Eğer YAML veya DB dışında yapmak istersen, mapping işini tamamen kod tabanında çözebilirsin. Bunun için birkaç best practice yaklaşım var.

1️⃣ Enum + Strategy Pattern (Kod içinde mapping)

Burada command ve cameraType’ı enum ile tanımlarsın, mapping işi tamamen kod tarafında enum üstünden yapılır.

Command Enum
public enum CameraCommandType {
SAVE_PRESET,
TO_LEFT,
TO_RIGHT,
ZOOM_IN,
ZOOM_OUT
// ... 60+ komut buraya
}

Camera Type Enum
public enum CameraType {
PTZ,
THERMAL,
RADAR
}

Strategy Interface
public interface CommandStrategy {
CommandResult execute(CameraCommand cmd);
}

Strategy Registry (Kod içinde Mapping)
import java.util.HashMap;
import java.util.Map;

@Component
public class CommandRegistry {

    private final Map<String, CommandStrategy> strategies = new HashMap<>();

    public CommandRegistry(List<CommandStrategy> implementations) {
        // Spring otomatik tüm CommandStrategy bean'lerini bulur
        implementations.forEach(impl -> {
            strategies.put(impl.getKey(), impl);
        });
    }

    public CommandStrategy getStrategy(CameraType type, CameraCommandType command) {
        return strategies.get(type.name() + "_" + command.name());
    }
}

Strategy Implementations
@Component
public class ThermalSavePresetStrategy implements CommandStrategy {

    @Override
    public CommandResult execute(CameraCommand cmd) {
        String presetName = (String) cmd.getParams().get("presetName");
        return CommandResult.ok("Thermal preset saved: " + presetName);
    }

    public String getKey() {
        return "THERMAL_SAVE_PRESET";
    }
}

@Component
public class PtzToLeftStrategy implements CommandStrategy {

    @Override
    public CommandResult execute(CameraCommand cmd) {
        return CommandResult.ok("PTZ moved left");
    }

    public String getKey() {
        return "PTZ_TO_LEFT";
    }
}

Dispatcher
@Component
public class CommandDispatcher {

    private final CommandRegistry registry;

    public CommandDispatcher(CommandRegistry registry) {
        this.registry = registry;
    }

    public CommandResult dispatch(CameraCommand cmd) {
        CommandStrategy strategy = registry.getStrategy(
                CameraType.valueOf(cmd.getCameraType().toUpperCase()),
                CameraCommandType.valueOf(cmd.getCommand().toUpperCase())
        );

        if (strategy == null) {
            return CommandResult.fail("No strategy found for " + cmd.getCommand());
        }

        return strategy.execute(cmd);
    }
}

2️⃣ Annotation + Reflection (Daha Dinamik)

Eğer mapping’i enum ile sabitlemek istemezsen, kendi annotation yazabilirsin:

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CameraCommandMapping {
CameraType type();
String command();
}

@CameraCommandMapping(type = CameraType.THERMAL, command = "SAVE_PRESET")
@Component
public class ThermalSavePresetExecutor implements CommandStrategy {
@Override
public CommandResult execute(CameraCommand cmd) {
return CommandResult.ok("Thermal preset saved");
}
}


Dispatcher Spring açılırken reflection ile bu annotation’ları tarar ve mapping tablosunu oluşturur.
Böylece YAML/DB yok, mapping kod üzerinden annotation ile çözülüyor.

🎯 Hangisi daha uygun?

Enum + Strategy:

Daha katı, compile-time güvenliği yüksek.

Komut seti stabilse (60+ ama belli, sık değişmeyecekse) ✅ bence en best practice bu.

Annotation + Reflection:

Daha esnek, mapping’i kolay ekleyip çıkarabilirsin.

Büyük ekiplerde "hangi komut hangi tipe denk geliyor" koddan görülebilir.

Ama compile-time güvenliği biraz daha düşük.

👉 Sana sorum: Bu 60+ komut çok sık değişecek / eklenecek mi, yoksa genelde sabit bir set mi olacak? Ona göre ben sana Enum-Strategy mi yoksa Annotation-Reflection skeleton’u çıkarayım.

Şunu dedin:
Benim methodlarim da map supplier yapısı içinde saklamam gerekiyor yani To_left için reqToLeft ve resToLeft cagirilacak bunlar mesaj üretecek
ChatGPT:

Tamam 👍 şimdi daha net oldu:

Senin ihtiyacın aslında sadece komut → handler mapping değil; her komut için request & response message üreten metod çiftleri de var. Yani:

TO_LEFT → reqToLeft() (request message) + resToLeft() (response parse)

SAVE_PRESET → reqSavePreset() + resSavePreset()

ve bunları kamera tipine göre çağırman gerekiyor.

Bu durumda Map + Supplier yapısı çok uygun 👍 Çünkü her komut için request/response üreticilerini bir arada saklayabilirsin.

🔹 Tasarım Önerisi: CommandDefinition
1. Ortak Command Definition
   public class CommandDefinition {
   private final Supplier<Object> requestSupplier;
   private final Function<Object, Object> responseHandler;

   public CommandDefinition(Supplier<Object> requestSupplier,
   Function<Object, Object> responseHandler) {
   this.requestSupplier = requestSupplier;
   this.responseHandler = responseHandler;
   }

   public Object buildRequest() {
   return requestSupplier.get();
   }

   public Object handleResponse(Object response) {
   return responseHandler.apply(response);
   }
   }

2. Kamera Tipine Göre Komut Tablosu

Her kamera tipi için bir Map<CommandType, CommandDefinition> saklarsın:

public enum CommandType {
TO_LEFT,
SAVE_PRESET,
ZOOM_IN
// ... 60+ komut
}

public class CameraCommandRegistry {

    private final Map<String, Map<CommandType, CommandDefinition>> registry = new HashMap<>();

    public CameraCommandRegistry() {
        // Thermal kamera komutları
        Map<CommandType, CommandDefinition> thermalCommands = new HashMap<>();
        thermalCommands.put(CommandType.SAVE_PRESET,
                new CommandDefinition(
                        () -> reqSavePreset(),
                        res -> resSavePreset(res)
                )
        );

        // PTZ kamera komutları
        Map<CommandType, CommandDefinition> ptzCommands = new HashMap<>();
        ptzCommands.put(CommandType.TO_LEFT,
                new CommandDefinition(
                        () -> reqToLeft(),
                        res -> resToLeft(res)
                )
        );

        registry.put("THERMAL", thermalCommands);
        registry.put("PTZ", ptzCommands);
    }

    public CommandDefinition getDefinition(String cameraType, CommandType command) {
        return registry.getOrDefault(cameraType, Map.of()).get(command);
    }

    // örnek methodlar
    private Object reqSavePreset() {
        return "thermal-save-preset-request"; // gerçek message
    }

    private Object resSavePreset(Object response) {
        return "thermal-save-preset-response: " + response;
    }

    private Object reqToLeft() {
        return "ptz-move-left-request";
    }

    private Object resToLeft(Object response) {
        return "ptz-move-left-response: " + response;
    }
}

3. Kullanım
   public class CommandDispatcher {
   private final CameraCommandRegistry registry;

   public CommandDispatcher(CameraCommandRegistry registry) {
   this.registry = registry;
   }

   public Object dispatch(String cameraType, CommandType command, Object rawResponse) {
   CommandDefinition def = registry.getDefinition(cameraType, command);
   if (def == null) {
   throw new IllegalArgumentException("No definition found for " + cameraType + " " + command);
   }

        Object request = def.buildRequest();
        System.out.println("Sending request: " + request);

        return def.handleResponse(rawResponse);
   }
   }

4. Örnek Çağrı
   public static void main(String[] args) {
   CameraCommandRegistry registry = new CameraCommandRegistry();
   CommandDispatcher dispatcher = new CommandDispatcher(registry);

   Object result = dispatcher.dispatch("PTZ", CommandType.TO_LEFT, "raw-ptz-response");
   System.out.println("Result: " + result);

   Object result2 = dispatcher.dispatch("THERMAL", CommandType.SAVE_PRESET, "raw-thermal-response");
   System.out.println("Result: " + result2);
   }


Output:

Sending request: ptz-move-left-request
Result: ptz-move-left-response: raw-ptz-response

Sending request: thermal-save-preset-request
Result: thermal-save-preset-response: raw-thermal-response

🎯 Avantajları

Her komut için reqX ve resX metotları tek bir CommandDefinition’da tutuluyor.

Supplier → request üretir.

Function → response’u parse eder.

Kamera tipine göre mapping ayrı tutulduğu için 60+ komut yönetilebilir.

Kod tarafında tamamen type-safe (YAML/DB gerekmez).

👉 İstersen ben bu yapıyı Spring Boot içinde Bean + @Configuration ile auto-register hale getirebilirim.
Böylece senin reqX/resX metotların doğrudan bean olarak toplanır, registry otomatik oluşur.

Bunu ister misin, yoksa bu “plain Java Map + Supplier” yaklaşımı senin için yeterli mi?

Şunu dedin:
Bu yapı ile beraber genel komutlar kamera türüne göre ve function yapısına göre tüm hepsini nasıl mapleyebilirim
ChatGPT:

Süper soru 👍
Senin ihtiyacın şu:

UI tarafında tek bir command seti var (TO_LEFT, SAVE_PRESET, ZOOM_IN …).

Backend’de bu komutları kamera tipine göre farklı request/response methodlarına map’lemen lazım.

Ayrıca req/res function çiftlerini birlikte saklaman lazım.

Ben sana genel bir pattern vereyim:

📐 Genel Yapı (Command Registry + Function Map)
1. CommandType Enum (tekil domain komut seti)
   public enum CommandType {
   TO_LEFT,
   TO_RIGHT,
   SAVE_PRESET,
   ZOOM_IN,
   ZOOM_OUT
   // ... 60+ komut
   }

2. CommandDefinition (req/res function çifti)
   import java.util.function.Function;
   import java.util.function.Supplier;

public class CommandDefinition {
private final Supplier<Object> requestSupplier;
private final Function<Object, Object> responseHandler;

    public CommandDefinition(Supplier<Object> requestSupplier, Function<Object, Object> responseHandler) {
        this.requestSupplier = requestSupplier;
        this.responseHandler = responseHandler;
    }

    public Object buildRequest() {
        return requestSupplier.get();
    }

    public Object handleResponse(Object response) {
        return responseHandler.apply(response);
    }
}

3. Registry: Kamera Tipine Göre Tüm Komutlar
   import java.util.EnumMap;
   import java.util.HashMap;
   import java.util.Map;

public class CameraCommandRegistry {

    // cameraType -> (commandType -> CommandDefinition)
    private final Map<String, Map<CommandType, CommandDefinition>> registry = new HashMap<>();

    public CameraCommandRegistry() {
        // PTZ kamera komutları
        Map<CommandType, CommandDefinition> ptzCommands = new EnumMap<>(CommandType.class);
        ptzCommands.put(CommandType.TO_LEFT,
                new CommandDefinition(this::reqPtzToLeft, this::resPtzToLeft));
        ptzCommands.put(CommandType.SAVE_PRESET,
                new CommandDefinition(this::reqPtzSavePreset, this::resPtzSavePreset));

        // Thermal kamera komutları
        Map<CommandType, CommandDefinition> thermalCommands = new EnumMap<>(CommandType.class);
        thermalCommands.put(CommandType.TO_LEFT,
                new CommandDefinition(this::reqThermalToLeft, this::resThermalToLeft));
        thermalCommands.put(CommandType.SAVE_PRESET,
                new CommandDefinition(this::reqThermalSavePreset, this::resThermalSavePreset));

        // Registry doldur
        registry.put("PTZ", ptzCommands);
        registry.put("THERMAL", thermalCommands);
    }

    public CommandDefinition getDefinition(String cameraType, CommandType command) {
        return registry.getOrDefault(cameraType, Map.of()).get(command);
    }

    // ---------- PTZ methodları ----------
    private Object reqPtzToLeft() { return "PTZ_REQ:MOVE_LEFT"; }
    private Object resPtzToLeft(Object res) { return "PTZ_RES:MOVE_LEFT -> " + res; }

    private Object reqPtzSavePreset() { return "PTZ_REQ:SAVE_PRESET"; }
    private Object resPtzSavePreset(Object res) { return "PTZ_RES:SAVE_PRESET -> " + res; }

    // ---------- Thermal methodları ----------
    private Object reqThermalToLeft() { return "THERMAL_REQ:MOVE_LEFT"; }
    private Object resThermalToLeft(Object res) { return "THERMAL_RES:MOVE_LEFT -> " + res; }

    private Object reqThermalSavePreset() { return "THERMAL_REQ:SAVE_PRESET"; }
    private Object resThermalSavePreset(Object res) { return "THERMAL_RES:SAVE_PRESET -> " + res; }
}

4. Dispatcher (tek noktadan çağrı)
   public class CommandDispatcher {

   private final CameraCommandRegistry registry;

   public CommandDispatcher(CameraCommandRegistry registry) {
   this.registry = registry;
   }

   public Object dispatch(String cameraType, CommandType command, Object rawResponse) {
   CommandDefinition def = registry.getDefinition(cameraType, command);

        if (def == null) {
            throw new IllegalArgumentException("No mapping found for " + cameraType + " - " + command);
        }

        Object request = def.buildRequest();
        System.out.println(">>> Sending request: " + request);

        return def.handleResponse(rawResponse);
   }
   }

5. Kullanım
   public class DemoApp {
   public static void main(String[] args) {
   CameraCommandRegistry registry = new CameraCommandRegistry();
   CommandDispatcher dispatcher = new CommandDispatcher(registry);

        // PTZ kamera için TO_LEFT komutu
        Object result1 = dispatcher.dispatch("PTZ", CommandType.TO_LEFT, "RAW_PTZ_RESPONSE");
        System.out.println("Result1 = " + result1);

        // Thermal kamera için SAVE_PRESET komutu
        Object result2 = dispatcher.dispatch("THERMAL", CommandType.SAVE_PRESET, "RAW_THERMAL_RESPONSE");
        System.out.println("Result2 = " + result2);
   }
   }


📌 Çıktı:

>>> Sending request: PTZ_REQ:MOVE_LEFT
Result1 = PTZ_RES:MOVE_LEFT -> RAW_PTZ_RESPONSE

>>> Sending request: THERMAL_REQ:SAVE_PRESET
Result2 = THERMAL_RES:SAVE_PRESET -> RAW_THERMAL_RESPONSE

🎯 Bu Yapının Avantajları

UI tarafı hep tekil komut seti kullanıyor (CommandType enum → 60+ komut).

Backend’de registry sayesinde kamera tipine göre farklı req/res function mapping’i kolayca yapılabiliyor.

Yeni komut eklemek = sadece registry.put("PTZ", new CommandDefinition(...)) eklemek.

Supplier + Function yapısı sayesinde hem request, hem response mantığı birlikte taşınıyor.

👉 Sana sorum: Bu mapping’i böyle kod içinden registry ile mi kurmak istersin,
yoksa Spring Boot’ta bean discovery (@Component/@Bean) ile otomatik toplansın mı?

(Birincisi daha kontrollü, ikincisi daha dinamik ve genişletilebilir olur.)