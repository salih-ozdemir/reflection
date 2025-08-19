package enumtype.ifaceenum;

public class CommandMapper {
    private static final Map<CommandType, BackEnum> MAPPING = Map.ofEntries(
            Map.entry(MoveCommand.LEFT, BackEnum.GOTO_POS),
            Map.entry(ZoomCommand.ZOOM_IN, BackEnum.RESET)
            // ...
    );

    public static BackEnum getBackCommand(CommandType type) {
        return MAPPING.get(type);
    }
}
