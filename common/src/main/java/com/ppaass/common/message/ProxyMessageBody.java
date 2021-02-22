package com.ppaass.common.message;

public class ProxyMessageBody extends MessageBody<ProxyMessageBodyType> {
    public ProxyMessageBody(String id, String userToken, String targetHost, int targetPort,
                            ProxyMessageBodyType bodyType, byte[] data) {
        super(id, userToken, targetHost, targetPort, bodyType, data);
    }
}
