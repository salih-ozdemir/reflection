package tr.com.sozdemir.ptz.advance;

// Command işaretleme annotation'ı
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PTZCommand {
    CommandType value();
}
