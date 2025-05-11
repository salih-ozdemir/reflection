package tr.com.sozdemir.compc;

public class UnitTypeGroupUsage1 {

    Class<?> type = field.getType();

UnitTypeGroup.from(type).ifPresentOrElse(group -> {
        Class<?> primitive = group.getJavaPrimitive();

        if (primitive == byte.class) {
            byte value = 1;
            System.out.println("Set byte: " + value);
        } else if (primitive == short.class) {
            short value = 1;
            System.out.println("Set short: " + value);
        } else if (primitive == int.class) {
            int value = 1;
            System.out.println("Set int: " + value);
        } else if (primitive == long.class) {
            long value = 1L;
            System.out.println("Set long: " + value);
        } else if (primitive == float.class) {
            float value = 1.0f;
            System.out.println("Set float: " + value);
        } else if (primitive == double.class) {
            double value = 1.0d;
            System.out.println("Set double: " + value);
        }
    }, () -> {
        System.out.println("Unknown type: " + type.getName());
    });
}


public void populateFields(Object obj) {
    Class<?> clazz = obj.getClass();

    for (Field field : clazz.getDeclaredFields()) {
        field.setAccessible(true);
        Class<?> type = field.getType();

        try {
            Object value = TypeDefaultValueProvider.getDefaultValue(type);
            if (value != null) {
                field.set(obj, value);
            }
        } catch (Exception e) {
            System.out.println("Hata: " + field.getName() + " -> " + e.getMessage());
        }
    }
}

public class TypeDefaultValueProvider {

    public static Object getDefaultValue(Class<?> type) {
        if (type == Int8.class || type == UInt8.class) {
            return (byte) 1;
        } else if (type == Int16.class || type == UInt16.class) {
            return (short) 1;
        } else if (type == Int32.class || type == UInt32.class) {
            return 1;
        } else if (type == Int64.class || type == UInt64.class) {
            return 1L;
        } else if (type == Float32.class) {
            return 1.0f;
        } else if (type == Float64.class) {
            return 1.0d;
        } else if (type == String.class) {
            return "default";
        } else if (BaseUnit.class.isAssignableFrom(type)) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("BaseUnit nesnesi üretilemedi: " + type.getName(), e);
            }
        }

        return null;
    }
}

////
public abstract class BaseUnit {
    public abstract Object getDefaultValue();  // her alt sınıf kendi primitive değerini döner
}
public class Int8 extends BaseUnit {
    @Override
    public Object getDefaultValue() {
        return (byte) 1;
    }
}

public class UInt8 extends BaseUnit {
    @Override
    public Object getDefaultValue() {
        return (byte) 1;
    }
}

public class Int16 extends BaseUnit {
    @Override
    public Object getDefaultValue() {
        return (short) 1;
    }
}

// ve diğerleri benzer şekilde...

public class Main {
    public static void main(String[] args) throws Exception {
        Class<?> type = UInt8.class;

        if (BaseUnit.class.isAssignableFrom(type)) {
            BaseUnit instance = (BaseUnit) type.getDeclaredConstructor().newInstance();
            Object value = instance.getDefaultValue();
            System.out.println("Default değer: " + value);
        } else {
            System.out.println("BaseUnit değil.");
        }
    }
}

////
