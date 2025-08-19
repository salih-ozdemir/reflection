package enumtype.ymltype;

import java.util.EnumMap;
import java.util.Map;

public class CommandMapper<T extends Enum<T>, U extends Enum<U>> {

    private final Map<T, U> mapping;

    public CommandMapper(Class<T> typeClass) {
        this.mapping = new EnumMap<>(typeClass);
    }

    public void register(T type, U back) {
        mapping.put(type, back);
    }

    public U get(T type) {
        return mapping.get(type);
    }

    public boolean contains(T type) {
        return mapping.containsKey(type);
    }

    public Map<T, U> getAll() {
        return Map.copyOf(mapping);
    }
}