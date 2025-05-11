package tr.com.sozdemir.comparator;

public class GrupluControl {
    public boolean isIntegerType(NumericType type) {
        return type == INT8 || type == INT16 || type == INT32;
    }

    public boolean isFloatingType(NumericType type) {
        return type == FLOAT || type == DOUBLE;
    }
}
