package com.ppaass.common.message;

public enum ProxyMessageBodyType implements MessageBodyType {
    /**
     * TCP data handled.
     */
    OK_TCP(0),
    /**
     * UDP data handled.
     */
    OK_UDP(1),
    /**
     * Heartbeat
     */
    HEARTBEAT(2),
    /**
     * Connection fail
     */
    CONNECT_FAIL(3),
    /**
     * Connection success
     */
    CONNECT_SUCCESS(4);
    private final int value;

    ProxyMessageBodyType(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return this.value;
    }
}
