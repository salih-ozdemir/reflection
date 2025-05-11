package tr.com.sozdemir.comparator;

public class TipDonusumu {
    public Object convertValue(NumericType targetType, Object value) {
        switch (targetType) {
            case INT8:
                return ((Number)value).byteValue();
            case INT16:
                return ((Number)value).shortValue();
            // Diğer tipler...
            default:
                throw new UnsupportedOperationException();
        }
    }

    //validasyon
    public void validateType(Field field, NumericType expectedType) {
        NumericType actualType = NumericType.fromClass(field.getType());
        if (actualType != expectedType) {
            throw new IllegalArgumentException(
                    String.format("Field %s must be %s but was %s",
                            field.getName(),
                            expectedType.getTypeName(),
                            actualType.getTypeName()));
        }
    }

}
