package tr.com.sozdemir.comparator;

public class FactoryPattern {
    public interface TypeHandler {
        void handle(Field field);
    }

    public class TypeHandlerFactory {
        private static final Map<NumericType, TypeHandler> HANDLERS = Map.of(
                NumericType.INT8, new Int8Handler(),
                NumericType.INT16, new Int16Handler()
                // Diğer tipler...
        );

        public static TypeHandler getHandler(Class<?> type) {
            return HANDLERS.getOrDefault(
                    NumericType.fromClass(type),
                    new DefaultHandler()
            );
        }
    }
}
