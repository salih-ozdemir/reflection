package tr.com.sozdemir.design1;

// Temel MessageSection arayüzü
interface MessageSection {
    byte[] toBytes();
    void validate() throws InvalidMessageException;
}