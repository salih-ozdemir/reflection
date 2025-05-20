package tr.com.sozdemir.ptz.advance;

// Spring Boot uygulamasında otomatik taranacak paket
@SpringBootApplication
@ComponentScan("com.ptz.commands")
public class PTZApplication {
    public static void main(String[] args) {
        SpringApplication.run(PTZApplication.class, args);
    }
}