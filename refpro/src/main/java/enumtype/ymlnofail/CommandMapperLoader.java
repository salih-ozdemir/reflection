package enumtype.ymlnofail;

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
            try {
                TypeEnum typeEnum = TypeEnum.valueOf(type);
                BackEnum backEnum = BackEnum.valueOf(back);
                mapper.register(typeEnum, backEnum);
            } catch (IllegalArgumentException e) {
                // HATA: enum bulunamadı
                throw new IllegalStateException(
                        String.format("Invalid mapping in config: %s -> %s", type, back), e
                );

                // Eğer fail fast istemezsen (sadece warn atıp devam etsin dersen):
                // log.warn("Skipping invalid mapping: {} -> {}", type, back);
            }
        });
    }

    public CommandMapper<TypeEnum, BackEnum> getMapper() {
        return mapper;
    }
}

Süper 👍 O zaman senin senaryonda config’ten gelen mapping yanlışsa uygulama hiç başlamasın (fail fast) daha güvenli olur.
Çünkü mapping hatalıysa backend yanlış çalışır, bu da fark edilmezse ciddi hata yaratabilir.

Ben sana fail fast + opsiyonel olarak warn ile devam etme örneğini vereyim 👇

log.warn("Skipping invalid mapping: {} -> {}", type, back);