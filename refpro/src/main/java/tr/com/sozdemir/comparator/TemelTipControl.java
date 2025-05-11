package tr.com.sozdemir.comparator;

public class TemelTipControl {
    public void control(){
        Class<?> fieldType = field.getType();
        NumericType numericType = NumericType.fromClass(fieldType);

        switch (numericType) {
            case INT8:
                // int8 özel işlemleri
                break;
            case INT16:
                // int16 özel işlemleri
                break;
            case UNKNOWN:
                // Bilinmeyen tip işlemleri
                break;
            default:
                // Diğer tipler için varsayılan işlemler
        }
    }
}
