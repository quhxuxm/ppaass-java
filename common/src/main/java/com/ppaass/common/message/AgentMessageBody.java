package com.ppaass.common.message;

public class AgentMessageBody extends MessageBody<AgentMessageBodyType> {
    public AgentMessageBody(String id, String userToken, String targetHost, int targetPort,
                            AgentMessageBodyType bodyType, byte[] data) {
        super(id, userToken, targetHost, targetPort, bodyType, data);
    }
}
