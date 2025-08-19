package enumtype.ymltype;

//🔹 Kullanım
@Service
public class CommandService {

    private final CommandMapper<TypeEnum, BackEnum> mapper;

    public CommandService(CommandMapperLoader loader) {
        this.mapper = loader.getMapper();
    }

    public BackEnum resolve(TypeEnum type) {
        return mapper.get(type);
    }
}