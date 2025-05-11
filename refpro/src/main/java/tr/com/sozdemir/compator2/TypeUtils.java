package tr.com.sozdemir.compator2;

public class TypeUtils {
    public static boolean isIntegerType(Class<?> clazz) {
        NumericType type = NumericType.fromClass(clazz);
        return type == INT8 || type == INT16 || type == INT32 ||
                type == UINT8 || type == UINT16 || type == UINT32;
    }

    public static boolean isSameSize(Class<?> type1, Class<?> type2) {
        return NumericType.fromClass(type1).byteSize ==
                NumericType.fromClass(type2).byteSize;
    }

    public static int getMaxValue(Class<?> numericType) {
        NumericType type = NumericType.fromClass(numericType);
        if (type.signed) {
            return (1 << (type.byteSize * 8 - 1)) - 1;
        } else {
            return (1 << (type.byteSize * 8)) - 1;
        }
    }
}