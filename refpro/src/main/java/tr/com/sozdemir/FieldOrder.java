package tr.com.sozdemir;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface FieldOrder {
    int value() default 0;
}