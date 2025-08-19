package enumtype.generic;

public enum TypeEnum {
    LEFT, RIGHT, ZOOM_IN, ZOOM_OUT
}

public enum BackEnum {
    GOTO_POS, STOP, RESET
}

public class Main {
    public static void main(String[] args) {
        CommandMapper<TypeEnum, BackEnum> mapper = new CommandMapper<>(TypeEnum.class);

        // mapping tanımlamaları
        mapper.register(TypeEnum.LEFT, BackEnum.GOTO_POS);
        mapper.register(TypeEnum.RIGHT, BackEnum.GOTO_POS);
        mapper.register(TypeEnum.ZOOM_IN, BackEnum.RESET);
        mapper.register(TypeEnum.ZOOM_OUT, BackEnum.STOP);

        // kullanım
        System.out.println(mapper.get(TypeEnum.LEFT));     // GOTO_POS
        System.out.println(mapper.get(TypeEnum.ZOOM_OUT)); // STOP

        // güvenlik
        System.out.println(mapper.contains(TypeEnum.RIGHT)); // true
    }
}