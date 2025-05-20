package tr.com.sozdemir.ptz;

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