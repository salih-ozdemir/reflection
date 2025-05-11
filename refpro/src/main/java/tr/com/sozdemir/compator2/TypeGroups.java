package tr.com.sozdemir.compator2;

public class TypeGroups {
    public static final EnumSet<NumericType> UNSIGNED_TYPES =
            EnumSet.of(UINT8, UINT16, UINT32);

    public static final EnumSet<NumericType> SIGNED_TYPES =
            EnumSet.of(INT8, INT16, INT32, FLOAT, DOUBLE);

    public static final EnumSet<NumericType> ALL_INTEGERS =
            EnumSet.of(INT8, INT16, INT32, UINT8, UINT16, UINT32);

    public static boolean isInGroup(Class<?> clazz, EnumSet<NumericType> group) {
        return group.contains(NumericType.fromClass(clazz));
    }
}
//Usage
// 1. Örnek: 8-bit tipler için işlem
if (TypeGroups.isInGroup(fieldType, TypeGroups.ALL_8BIT_TYPES)) {
byte[] buffer = new byte[1];
// ... özel işlemler
}

// 2. Örnek: İşaretsiz tipler için validasyon
        if (TypeGroups.isInGroup(value.getClass(), TypeGroups.UNSIGNED_TYPES)) {
        if (((Number)value).longValue() < 0) {
        throw new IllegalArgumentException("Negative value for unsigned type");
    }
            }

// 3. Örnek: Tip dönüşümü
public static Object convertToUnsigned(Number value, Class<?> targetType) {
    if (!TypeGroups.isInGroup(targetType, TypeGroups.UNSIGNED_TYPES)) {
        throw new IllegalArgumentException("Target must be unsigned type");
    }
    // ... dönüşüm işlemleri
}
