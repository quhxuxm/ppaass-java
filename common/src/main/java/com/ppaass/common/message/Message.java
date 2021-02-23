package com.ppaass.common.message;

import com.ppaass.common.cryptography.EncryptionType;

public abstract class Message<T extends MessageBodyType> {
    private final byte[] encryptionToken;
    private final EncryptionType encryptionType;
    private final MessageBody<T> body;

    public Message(byte[] encryptionToken, EncryptionType encryptionType,
                   MessageBody<T> body) {
        this.encryptionToken = encryptionToken;
        this.encryptionType = encryptionType;
        this.body = body;
    }

    public byte[] getEncryptionToken() {
        return encryptionToken;
    }

    public EncryptionType getEncryptionType() {
        return encryptionType;
    }

    public MessageBody<T> getBody() {
        return body;
    }
}
