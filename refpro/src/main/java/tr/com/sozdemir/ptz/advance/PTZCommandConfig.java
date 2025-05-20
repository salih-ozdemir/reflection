package tr.com.sozdemir.ptz.advance;

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