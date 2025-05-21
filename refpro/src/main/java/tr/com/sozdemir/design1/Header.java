package tr.com.sozdemir.design1;

// Header bölümü
class Header implements MessageSection {
    private final String messageType;
    private final int messageId;
    private final long timestamp;

    public Header(String messageType, int messageId) {
        this.messageType = messageType;
        this.messageId = messageId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessageType() {
        return messageType;
    }

    public int getMessageId() {
        return messageId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public byte[] toBytes() {
        // Byte dönüşümü için gerçek bir uygulama daha detaylı olmalı
        return (messageType + "|" + messageId + "|" + timestamp).getBytes();
    }

    @Override
    public void validate() throws InvalidMessageException {
        if (messageType == null || messageType.isEmpty()) {
            throw new InvalidMessageException("Message type cannot be empty");
        }
        if (messageId <= 0) {
            throw new InvalidMessageException("Message ID must be positive");
        }
    }
}
