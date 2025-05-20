package tr.com.sozdemir.ptz.advance;

// Annotation işlemci sınıfı
public class CommandScanner {
    public static void scanAndRegister(String basePackage) {
        Reflections reflections = new Reflections(basePackage);

        // Request sınıflarını bul
        Set<Class<?>> requestClasses = reflections.getTypesAnnotatedWith(PTZCommand.class);

        for (Class<?> clazz : requestClasses) {
            if (Request.class.isAssignableFrom(clazz)) {
                PTZCommand annotation = clazz.getAnnotation(PTZCommand.class);
                CommandType commandType = annotation.value();

                // İlgili Response sınıfını bul (Request sınıf adından "Request" kısmını "Response" ile değiştirerek)
                String responseClassName = clazz.getName().replace("Request", "Response");
                try {
                    Class<?> responseClass = Class.forName(responseClassName);

                    // Registry'e kaydet
                    CommandRegistry.register(
                            commandType,
                            (Supplier<Request>) () -> {
                                try {
                                    return (Request) clazz.getDeclaredConstructor().newInstance();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            (Supplier<Response>) () -> {
                                try {
                                    return (Response) responseClass.getDeclaredConstructor().newInstance();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    );
                } catch (ClassNotFoundException e) {
                    System.err.println("Response sınıfı bulunamadı: " + responseClassName);
                }
            }
        }
    }
}