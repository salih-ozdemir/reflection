package enumtype.ifaceenum;

public class CommandTypeResolver {

    private static final List<Class<? extends Enum<? extends CommandType>>> ENUM_GROUPS = List.of(
            MoveCommand.class,
            ZoomCommand.class,
            PositionCommand.class
    );

    public static CommandType fromString(String value) {
        for (Class<? extends Enum<? extends CommandType>> enumClass : ENUM_GROUPS) {
            for (Enum<?> constant : enumClass.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(value)) {
                    return (CommandType) constant;
                }
            }
        }
        throw new IllegalArgumentException("Unknown command: " + value);
    }
}
