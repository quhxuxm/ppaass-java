package com.ppaass.common.message;

public abstract class MessageBody<T extends MessageBodyType> {
    private final String id;
    private final String userToken;
    private final String targetHost;
    private final int targetPort;
    private final T bodyType;
    private final byte[] data;

    public MessageBody(String id, String userToken, String targetHost, int targetPort, T bodyType, byte[] data) {
        this.id = id;
        this.userToken = userToken;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.bodyType = bodyType;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public String getUserToken() {
        return userToken;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public T getBodyType() {
        return bodyType;
    }

    public byte[] getData() {
        return data;
    }
}
