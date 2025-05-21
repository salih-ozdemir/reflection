package tr.com.sozdemir.design1;

// Özel bir exception sınıfı
class InvalidMessageException extends Exception {
    public InvalidMessageException(String message) {
        super(message);
    }
}