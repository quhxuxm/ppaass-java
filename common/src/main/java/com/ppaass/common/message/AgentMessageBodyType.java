package com.ppaass.common.message;

public enum AgentMessageBodyType implements MessageBodyType {
    /**
     * Create a connection on proxy and keep alive
     */
    CONNECT_WITH_KEEP_ALIVE(0),
    /**
     * Create a connection on proxy and do not keep alive
     */
    CONNECT_WITHOUT_KEEP_ALIVE(1),
    /**
     * Sending a TCP data
     */
    TCP_DATA(2),
    /**
     * Sending a UDP data
     */
    UDP_DATA(3);
    private final int value;

    AgentMessageBodyType(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return this.value;
    }
}
