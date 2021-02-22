package com.ppaass.common.cryptography;

import java.util.Random;

public enum EncryptionType {
    /**
     * The aes encryption
     */
    AES(0),
    /**
     * The blowfish encryption
     */
    BLOWFISH(1);
    private static final Random random = new Random();

    public static EncryptionType choose() {
        int index = random.nextInt(EncryptionType.values().length);
        return EncryptionType.values()[index];
    }

    private final int value;

    EncryptionType(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }
}
