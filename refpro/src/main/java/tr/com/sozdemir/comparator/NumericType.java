package tr.com.sozdemir.comparator;

public enum NumericType {
    INT8("int8", int8.class, 1),
    INT16("int16", int16.class, 2),
    INT32("int32", int32.class, 4),
    FLOAT("float", float.class, 4),
    DOUBLE("double", double.class, 8),
    UNKNOWN("unknown", null, 0);

    private final String typeName;
    private final Class<?> typeClass;
    private final int byteSize;

    NumericType(String typeName, Class<?> typeClass, int byteSize) {
        this.typeName = typeName;
        this.typeClass = typeClass;
        this.byteSize = byteSize;
    }

    // Sınıf tipinden enum değerini bulma
    public static NumericType fromClass(Class<?> clazz) {
        for (NumericType type : values()) {
            if (type.typeClass != null && type.typeClass.equals(clazz)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    // String isminden enum değerini bulma
    public static NumericType fromName(String name) {
        for (NumericType type : values()) {
            if (type.typeName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    // Getter metodları
    public String getTypeName() { return typeName; }
    public Class<?> getTypeClass() { return typeClass; }
    public int getByteSize() { return byteSize; }
}