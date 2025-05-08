package tr.com.sozdemir;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class FieldOrderUtils {

    public static List<Field> getAllFieldsOrdered(Class<?> clazz) {
        List<Field> allFields = new ArrayList<>();

        // Tüm hiyerarşiyi topla (base class'tan başlayarak)
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            allFields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        // FieldOrder annotation'ına göre sırala
        return allFields.stream()
                .sorted(Comparator.comparingInt(f -> {
                    FieldOrder order = f.getAnnotation(FieldOrder.class);
                    return order != null ? order.value() : Integer.MAX_VALUE;
                }))
                .collect(Collectors.toList());
    }

    public static void printFieldsInOrder(Class<?> clazz) {
        List<Field> fields = getAllFieldsOrdered(clazz);

        System.out.println("Field order for " + clazz.getSimpleName() + ":");
        for (Field field : fields) {
            FieldOrder order = field.getAnnotation(FieldOrder.class);
            System.out.printf("%s.%s (@FieldOrder(%d))%n",
                    field.getDeclaringClass().getSimpleName(),
                    field.getName(),
                    order != null ? order.value() : -1);
        }
    }

    public static List<Field> getAllFieldsOrderedWithClassPriority(Class<?> clazz) {
        List<Class<?>> classHierarchy = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            classHierarchy.add(current);
            current = current.getSuperclass();
        }

        // Base class'lar önce gelecek şekilde ters çevir
        Collections.reverse(classHierarchy);

        return classHierarchy.stream()
                .flatMap(c -> Arrays.stream(c.getDeclaredFields()))
                .sorted(Comparator.comparingInt(f -> {
                    FieldOrder order = f.getAnnotation(FieldOrder.class);
                    return order != null ? order.value() : Integer.MAX_VALUE;
                }))
                .collect(Collectors.toList());
    }
}