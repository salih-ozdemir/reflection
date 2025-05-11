package tr.com.sozdemir.compator2;

public class UseExample {
    public void TemelTip(){
        Class<?> fieldType = field.getType();

// 8-bit kontrolü (hem int8 hem uint8)
        if (NumericType.is8Bit(fieldType)) {
            // 8-bit işlemleri
        }

// İşaretli/işaretsiz kontrol
        if (NumericType.isSigned(fieldType)) {
            // İşaretli tipler için
        } else if (NumericType.isUnsigned(fieldType)) {
            // İşaretsiz tipler için
        }
    }

    public void SwitchTip(){
        switch (NumericType.fromClass(fieldType)) {
            case INT8, UINT8 -> handle8Bit(field);
            case INT16, UINT16 -> handle16Bit(field);
            case INT32, UINT32 -> handle32Bit(field);
            default -> handleUnknown(field);
        }
    }
}
