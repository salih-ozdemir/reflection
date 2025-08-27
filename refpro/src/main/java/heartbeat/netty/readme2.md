ui tarafında joystick ve diğer mesajlar websocket üzerinden iletiliyor. socket mantığı naısl olmalı. joystick ve diğer komutlar için ayrı socketler mi oluşturmak mantıklı. backend tarafının örnek kodunu yazabilir misin
ChatGPT:

Süper soru 👍 çünkü burada UI → Backend haberleşme WebSocket ile, Backend → Kamera (UDP/Netty) haberleşmesi ise hibrit model ile.

Bunu netleştirelim:

🔹 WebSocket Tasarımı (UI ↔ Backend)

Ayrı WebSocket açmaya gerek yok (joystick ve diğer komutlar için).

Tek bir WebSocket connection yeterli, mesaj formatını ayırırsın:

"type": "joystick" → backend doğrudan UDP’ye fire-and-forget gönderir.

"type": "command" → backend UDP’ye corrId ile gönderir, cevabı gelince aynı WebSocket üzerinden UI’a push eder.

Bu sayede:

UI tarafı tek WebSocket açar → daha az bağlantı yükü.

Backend tarafında tek Netty UDP client var, joystick ve management birlikte gider ama farklı mantıkta işlenir.

🔹 Backend Kod (Spring Boot + WebSocket + Netty UDP)

Aşağıdaki örnekte:

/ws/control endpoint’i var

Gelen JSON mesajları type alanına göre ayrılıyor

Joystick mesajı → direkt UDP’ye fire-and-forget

Command mesajı → UDP’ye corrId ile gönderiliyor, cevap geldiğinde WebSocket session’a gönderiliyor

// ===================== WebSocket Config =====================
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
private final ControlWebSocketHandler handler;

    public WebSocketConfig(ControlWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/control").setAllowedOrigins("*");
    }
}

// ===================== WebSocket Handler =====================
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.concurrent.CompletableFuture;

@Component
public class ControlWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();
    private final UdpHybridClient udpClient;

    public ControlWebSocketHandler(UdpHybridClient udpClient) {
        this.udpClient = udpClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("WebSocket connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = mapper.readTree(message.getPayload());
        String type = json.get("type").asText();

        if ("joystick".equals(type)) {
            // 🎮 Fire-and-forget
            String direction = json.get("command").asText();
            udpClient.sendJoystickCommand(direction);

        } else if ("command".equals(type)) {
            // ⚙️ Queue + corrId
            String command = json.get("command").asText();

            CompletableFuture<String> resp = udpClient.sendManagementCommand(command);
            resp.thenAccept(result -> {
                try {
                    session.sendMessage(new TextMessage(
                            mapper.createObjectNode()
                                    .put("type", "response")
                                    .put("command", command)
                                    .put("result", result)
                                    .toString()
                    ));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
}

// ===================== Spring Bean Olarak UDP Client =====================
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UdpClientConfig {

    @Bean(destroyMethod = "stop")
    public UdpHybridClient udpHybridClient() throws Exception {
        UdpHybridClient client = new UdpHybridClient("127.0.0.1", 9000);
        client.start();
        return client;
    }
}

🔹 UI Tarafında Gönderilen Mesaj Örnekleri

👉 Joystick (fire-and-forget)

{
"type": "joystick",
"command": "LEFT"
}


👉 Yönetim komutu (cevap beklenen)

{
"type": "command",
"command": "ZOOM_IN"
}


👉 Backend’ten gelen cevap

{
"type": "response",
"command": "ZOOM_IN",
"result": "OK"
}

🔹 Özet

Tek WebSocket → mesaj tipleriyle ayır

Backend tarafında Netty UDP Client hibrit çalışıyor:

Joystick → fire-and-forget

Management → corrId ile response bekleme

Böylece UI tarafında tek WebSocket bağlantısı, backend tarafında tek UDP channel kullanıyorsun.

👉 İstersen sana UDP Server tarafının Netty örneğini de yazabilirim (response üreten). Bunu da eklememi ister misin?