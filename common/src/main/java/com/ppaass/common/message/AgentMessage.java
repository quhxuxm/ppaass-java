package com.ppaass.common.message;

import com.ppaass.common.cryptography.EncryptionType;

public class AgentMessage extends Message<AgentMessageBodyType> {
    public AgentMessage(byte[] encryptionToken, EncryptionType encryptionType,
                        AgentMessageBody body) {
        super(encryptionToken, encryptionType, body);
    }
}
