package com.ppaass.common.message;

import com.ppaass.common.cryptography.EncryptionType;

public class ProxyMessage extends Message<ProxyMessageBodyType> {
    public ProxyMessage(byte[] encryptionToken, EncryptionType encryptionType,
                        ProxyMessageBody body) {
        super(encryptionToken, encryptionType, body);
    }
}
