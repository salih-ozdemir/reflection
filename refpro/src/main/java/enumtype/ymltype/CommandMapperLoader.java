package enumtype.ymltype;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class CommandMapperLoader {

    private final CommandProperties properties;
    private final CommandMapper<TypeEnum, BackEnum> mapper;

    public CommandMapperLoader(CommandProperties properties) {
        this.properties = properties;
        this.mapper = new CommandMapper<>(TypeEnum.class);
    }

    @PostConstruct
    public void init() {
        properties.getMappings().forEach((type, back) -> {
            mapper.register(TypeEnum.valueOf(type), BackEnum.valueOf(back));
        });
    }

    public CommandMapper<TypeEnum, BackEnum> getMapper() {
        return mapper;
    }
}