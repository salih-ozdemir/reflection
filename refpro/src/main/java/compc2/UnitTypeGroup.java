package compc2;

import java.util.*;

public enum UnitTypeGroup {
    INT8_GROUP(Set.of(Int8.class, UInt8.class), byte.class),
    INT16_GROUP(Set.of(Int16.class, UInt16.class), short.class),
    INT32_GROUP(Set.of(Int32.class, UInt32.class), int.class),
    INT64_GROUP(Set.of(Int64.class, UInt64.class), long.class),
    FLOAT_GROUP(Set.of(Float32.class), float.class),
    DOUBLE_GROUP(Set.of(Float64.class), double.class);

    private final Set<Class<?>> types;
    private final Class<?> javaPrimitive;

    UnitTypeGroup(Set<Class<?>> types, Class<?> javaPrimitive) {
        this.types = types;
        this.javaPrimitive = javaPrimitive;
    }

    public boolean matches(Class<?> type) {
        return types.contains(type);
    }

    public Class<?> getJavaPrimitive() {
        return javaPrimitive;
    }

    public static Optional<UnitTypeGroup> from(Class<?> type) {
        return Arrays.stream(values())
                .filter(g -> g.matches(type))
                .findFirst();
    }
}

public class TypeMapper {

    public static void handleType(Class<?> type) {
        UnitTypeGroup.from(type).ifPresentOrElse(group -> {
            Class<?> primitive = group.getJavaPrimitive();

            if (primitive == byte.class) {
                byte val = 1;
                System.out.println("Byte işlem: " + val);
            } else if (primitive == short.class) {
                short val = 1;
                System.out.println("Short işlem: " + val);
            } else if (primitive == int.class) {
                int val = 1;
                System.out.println("Int işlem: " + val);
            } else if (primitive == long.class) {
                long val = 1L;
                System.out.println("Long işlem: " + val);
            } else if (primitive == float.class) {
                float val = 1.0f;
                System.out.println("Float işlem: " + val);
            } else if (primitive == double.class) {
                double val = 1.0;
                System.out.println("Double işlem: " + val);
            }
        }, () -> {
            System.out.println("Tanımsız tür: " + type.getName());
        });
    }
}

public class Main {
    public static void main(String[] args) {
        TypeMapper.handleType(UInt8.class);   // Byte işlem
        TypeMapper.handleType(Int16.class);   // Short işlem
        TypeMapper.handleType(Float64.class); // Double işlem
    }
}
