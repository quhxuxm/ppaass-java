package com.ppaass.proxy.handler.bo;

import com.ppaass.protocol.vpn.message.AgentMessage;

public class UdpMessage {
    private final AgentMessage agentMessage;

    public UdpMessage(AgentMessage agentMessage) {
        this.agentMessage = agentMessage;
    }

    public AgentMessage getAgentMessage() {
        return agentMessage;
    }
}
