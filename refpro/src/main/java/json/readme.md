Spring bootta modellerim var ve bunları reflection ile dolduruyorum. Buradaki variable json formatına çevirmen gerekecek. Japon formatimda şu şekilde: {
Key:"",
Value:""
}
Ben ise modelde sysStatus=1 tutuyorum örneğin. Bunu şu şekilde Json formatına aktarmak lazım key:"system status"  value:1. Yani key özel bir anlamlandirma ile cozebilirim. Yani değişken üstüne annotasyon ile uzun ismini yazarım olanları uzun isimden olmayanları key ismi gelecek şekilde json formatına çevirmem lazim
ChatGPT:

Anladım 👍 Senin senaryoda yapmak istediğin şey şu:

Spring Boot’ta model sınıfların var (örneğin DeviceStatus gibi).

Bu sınıfların alanlarını Reflection ile okuyorsun.

Çıktıyı senin özel JSON formatına dönüştürmek istiyorsun:

[
{
"key": "system status",
"value": 1
}
]


Alanın üstüne bir annotasyon koyarak key adını özelleştirebileceksin. Eğer annotasyon yoksa, alanın ismi direkt key olacak.

Bunun için özel bir annotation + serializer yazabiliriz.

1. Annotation tanımla
   import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonKeyName {
String value();
}

2. Model örneği
   public class DeviceStatus {

   @JsonKeyName("system status")
   private int sysStatus = 1;

   private String deviceName = "Camera-01"; // annotation yoksa alan adı kullanılacak

   // getter / setter
   }

3. Reflection ile JSON dönüştürücü

Jackson kullanmadan kendin yazabilirsin:

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.*;

public class JsonKeyValueConverter {

    public static List<Map<String, Object>> convertToJson(Object obj) throws IllegalAccessException {
        List<Map<String, Object>> result = new ArrayList<>();

        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            Object value = field.get(obj);

            String keyName;
            if (field.isAnnotationPresent(JsonKeyName.class)) {
                keyName = field.getAnnotation(JsonKeyName.class).value();
            } else {
                keyName = field.getName();
            }

            Map<String, Object> entry = new HashMap<>();
            entry.put("key", keyName);
            entry.put("value", value);

            result.add(entry);
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        DeviceStatus status = new DeviceStatus();
        List<Map<String, Object>> jsonList = convertToJson(status);

        ObjectMapper mapper = new ObjectMapper();
        String jsonOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonList);

        System.out.println(jsonOutput);
    }
}

4. Çıktı
   [
   {
   "key" : "system status",
   "value" : 1
   },
   {
   "key" : "deviceName",
   "value" : "Camera-01"
   }
   ]


👉 Böylece:

Annotasyon varsa uzun isim (system status) gelir.

Yoksa alan adı (deviceName) gelir.

İstersen bu yapıyı Spring Boot tarafında otomatik JSON serializer haline getirip, REST API dönerken de bu formatı kullanabilirsin. Bunu da Jackson Serializer yazarak yapabilirim sana.

Bunu sadece özel method çağrısında mı kullanacaksın, yoksa Spring Boot REST API JSON dönüşünde de otomatik böyle olmasını istiyor musun?

Şunu dedin:
Özel bir method çağırarak aslında statü mesajları çağırarak her 5 sn de cihazdan değerler alacağım Json olarak gonderecegim
ChatGPT:

Tamamdır 👍 o zaman en doğru çözüm:

Cihazdan statü verilerini model objesi olarak alıyorsun (DeviceStatus gibi).

Her 5 saniyede bir, bu objeyi senin istediğin özel JSON formatına çevirip API’den publish edeceksin veya başka servise göndereceksin.

Yani sadece özel bir method bu dönüşümü yapacak → Spring Boot’un normal Jackson serialization yapısına dokunmana gerek yok.

Ben sana bunun için basit ama genişletilebilir bir çözüm şeması vereyim:

1. Annotation (alan için key uzun adı)
   import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonKeyName {
String value();
}

2. Model Örneği
   public class DeviceStatus {

   @JsonKeyName("system status")
   private int sysStatus = 1;

   @JsonKeyName("temperature")
   private double temp = 36.5;

   private String deviceName = "Camera-01"; // annotation yoksa alan adı gelir

   // getter / setter
   }

3. Reflection ile Özel JSON Formatı
   import com.fasterxml.jackson.databind.ObjectMapper;
   import java.lang.reflect.Field;
   import java.util.*;

public class JsonKeyValueConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String convertToCustomJson(Object obj) throws IllegalAccessException {
        List<Map<String, Object>> result = new ArrayList<>();

        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(obj);

            String keyName = field.isAnnotationPresent(JsonKeyName.class)
                    ? field.getAnnotation(JsonKeyName.class).value()
                    : field.getName();

            Map<String, Object> entry = new HashMap<>();
            entry.put("key", keyName);
            entry.put("value", value);

            result.add(entry);
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("JSON dönüşüm hatası", e);
        }
    }
}

4. Kullanım
   public class StatusSender {
   public static void main(String[] args) throws Exception {
   DeviceStatus status = new DeviceStatus();

        // JSON formatına dönüştür
        String jsonOutput = JsonKeyValueConverter.convertToCustomJson(status);

        System.out.println(jsonOutput);

        // Burada jsonOutput’u 5 sn’de bir publish edebilirsin
        // Örn: Kafka, WebSocket, REST client, MQTT, vs.
   }
   }

5. Çıktı
   [
   {
   "key" : "system status",
   "value" : 1
   },
   {
   "key" : "temperature",
   "value" : 36.5
   },
   {
   "key" : "deviceName",
   "value" : "Camera-01"
   }
   ]


Böylece:

Annotasyon varsa (@JsonKeyName("system status")) → uzun isim alınır.

Annotasyon yoksa (deviceName) → alan adı direkt alınır.

👉 Şimdi sana şunu sorayım:
Bu JSON çıktısını tek tek alanlar (liste halinde) mi göndereceksin, yoksa anahtarları tek seferde tek bir JSON objesi içinde göndermen daha uygun olur mu? (yani { "system status": 1, "temperature": 36.5 } gibi).

Seninki liste formatında, ama diğer tarafta tüketen servis bunu liste halinde mi bekliyor?

Şunu dedin:
Bi de bu json biraz genişletmem lazım. Key value status gibi. Bazı value değerleri o durumun cihaz için hatalı veya sorunsuz olduğunu belirliyor. Hepsinde değil ama bir kısmında örn 1 init 2 success 3 fail gibi. Değer 3 ise status belirledigim hata enim dan değer alacak örneğin critical gibi
ChatGPT:

Süper 👍 Şimdi iş daha da netleşti.
Senin istediğin yapı aslında status mapping içeriyor:

Bazı alanlarda value sadece sayı değil, aynı zamanda bir durum karşılığı var (ör: 1=INIT, 2=SUCCESS, 3=FAIL).

Eğer böyle bir mapping varsa, JSON’da "status" alanı da gözüksün.

Eğer mapping tanımlı değilse, sadece key ve value yeterli.

Çözüm Yaklaşımı

Bunu için iki şey ekleyelim:

Enum ile değer–status eşleştirmesi

Annotasyon içine, hangi enum’un kullanılacağını belirtecek parametre

1. Status Enum Örneği
   public enum SysStatusEnum {
   INIT(1, "init"),
   SUCCESS(2, "success"),
   FAIL(3, "critical");

   private final int code;
   private final String status;

   SysStatusEnum(int code, String status) {
   this.code = code;
   this.status = status;
   }

   public static String fromCode(int code) {
   for (SysStatusEnum e : values()) {
   if (e.code == code) {
   return e.status;
   }
   }
   return null; // eşleşme yoksa null döner
   }
   }

2. Annotation Genişletme
   import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonKeyName {
String value();
Class<? extends Enum<?>> statusEnum() default DefaultEnum.class;
}

// boş enum (placeholder)
enum DefaultEnum { }

3. Model
   public class DeviceStatus {

   @JsonKeyName(value = "system status", statusEnum = SysStatusEnum.class)
   private int sysStatus = 3;

   @JsonKeyName("temperature")
   private double temp = 36.5;

   private String deviceName = "Camera-01";
   }

4. Converter (Reflection ile)
   import com.fasterxml.jackson.databind.ObjectMapper;
   import java.lang.reflect.*;
   import java.util.*;

public class JsonKeyValueConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String convertToCustomJson(Object obj) throws IllegalAccessException {
        List<Map<String, Object>> result = new ArrayList<>();
        Class<?> clazz = obj.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(obj);

            String keyName;
            String status = null;

            if (field.isAnnotationPresent(JsonKeyName.class)) {
                JsonKeyName annotation = field.getAnnotation(JsonKeyName.class);
                keyName = annotation.value();

                // Eğer statusEnum belirtilmişse, enum’dan status çöz
                if (!annotation.statusEnum().equals(DefaultEnum.class) && value != null) {
                    status = resolveEnumStatus(annotation.statusEnum(), value);
                }
            } else {
                keyName = field.getName();
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", keyName);
            entry.put("value", value);
            if (status != null) {
                entry.put("status", status);
            }

            result.add(entry);
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("JSON dönüşüm hatası", e);
        }
    }

    private static String resolveEnumStatus(Class<? extends Enum<?>> enumClass, Object value) {
        try {
            Method fromCode = enumClass.getMethod("fromCode", int.class);
            return (String) fromCode.invoke(null, ((Number) value).intValue());
        } catch (Exception e) {
            return null;
        }
    }
}

5. Çıktı

Eğer sysStatus=3 ise:

[
{
"key" : "system status",
"value" : 3,
"status" : "critical"
},
{
"key" : "temperature",
"value" : 36.5
},
{
"key" : "deviceName",
"value" : "Camera-01"
}
]


🔑 Böylece:

Annotasyonda statusEnum tanımlanırsa → status alanı otomatik eklenir.

Tanımlı değilse → sadece key ve value döner.

İstiyorsan status alanının içine sadece string değil, "code" + "description" şeklinde bir JSON da koyabiliriz (ör: "status": {"code":3,"desc":"critical"}) ki daha esnek olur.

Bunu sen JSON’da nasıl görmek istersin? Tek string ("critical") mi yoksa detaylı JSON ({"code":3,"desc":"critical"}) mi?