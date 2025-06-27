package ethernets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.io.IOException;

@Component
public class MyEthernetClient {

    private final EthernetService ethernetService;

    @Autowired
    public MyEthernetClient(EthernetService ethernetService) {
        this.ethernetService = ethernetService;
    }

    @PostConstruct
    public void setup() {
        // Set up a listener for incoming data
        ethernetService.setDataReceivedListener(data -> {
            System.out.println("MyEthernetClient received data: " + new String(data));
            // Process the received data here
        });

        // You can also periodically check connection status or trigger actions
        // based on the status if needed.
    }

    public void sendCommand(String command) {
        try {
            if (ethernetService.getConnectionStatus() == EthernetService.ConnectionStatus.CONNECTED) {
                ethernetService.sendData(command.getBytes());
            } else {
                System.out.println("Cannot send command, not connected. Current status: " + ethernetService.getConnectionStatus());
                // Optionally, trigger a reconnect or queue the command
            }
        } catch (IOException e) {
            System.err.println("Failed to send command: " + e.getMessage());
        }
    }

    // Example method to demonstrate sending data
    public void sendHello() {
        sendCommand("Hello Ethernet Device!");
    }
}