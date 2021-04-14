package com.ppaass.common.message;

public enum ProxyMessageBodyType implements MessageBodyType {
    /**
     * TCP data handled.
     */
    OK_TCP(0),
    CLOSE_TCP(2),
    /**
     * UDP data handled.
     */
    OK_UDP(1),
    /**
     * Connection fail
     */
    CONNECT_FAIL(3),
    /**
     * Connection success
     */
    CONNECT_SUCCESS(4),
    /**
     * Fail on transfer TCP data
     */
    FAIL_TCP(5),
    /**
     * Fail on transfer UDP data
     */
    FAIL_UDP(6);
    private final int value;

    ProxyMessageBodyType(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return this.value;
    }
}
