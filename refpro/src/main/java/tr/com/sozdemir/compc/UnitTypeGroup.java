package tr.com.sozdemir.compc;

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