package tr.com.sozdemir.ptz;

public class ExampleImplement {

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
}
