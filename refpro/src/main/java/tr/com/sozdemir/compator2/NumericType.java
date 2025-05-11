package tr.com.sozdemir.compator2;

public enum NumericType {
    // İşaretli tipler
    INT8("int8", SignedInt8.class, 1, true),
    INT16("int16", SignedInt16.class, 2, true),
    INT32("int32", SignedInt32.class, 4, true),

    // İşaretsiz tipler
    UINT8("uint8", UnsignedInt8.class, 1, false),
    UINT16("uint16", UnsignedInt16.class, 2, false),
    UINT32("uint32", UnsignedInt32.class, 4, false),

    // Diğer tipler
    FLOAT("float", float.class, 4, true),
    DOUBLE("double", double.class, 8, true),
    UNKNOWN("unknown", null, 0, false);

    private final String typeName;
    private final Class<?> typeClass;
    private final int byteSize;
    private final boolean signed;

    NumericType(String typeName, Class<?> typeClass, int byteSize, boolean signed) {
        this.typeName = typeName;
        this.typeClass = typeClass;
        this.byteSize = byteSize;
        this.signed = signed;
    }

    // Tip kontrol metodları
    public static boolean is8Bit(Class<?> clazz) {
        NumericType type = fromClass(clazz);
        return type == INT8 || type == UINT8;
    }

    public static boolean is16Bit(Class<?> clazz) {
        NumericType type = fromClass(clazz);
        return type == INT16 || type == UINT16;
    }

    public static boolean isSigned(Class<?> clazz) {
        NumericType type = fromClass(clazz);
        return type != UNKNOWN && type.signed;
    }

    public static boolean isUnsigned(Class<?> clazz) {
        NumericType type = fromClass(clazz);
        return type != UNKNOWN && !type.signed;
    }

    private static NumericType fromClass(Class<?> clazz) {
        for (NumericType type : values()) {
            if (type.typeClass != null && type.typeClass.equals(clazz)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
