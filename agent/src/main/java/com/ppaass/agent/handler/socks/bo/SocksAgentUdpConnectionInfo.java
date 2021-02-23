package com.ppaass.agent.handler.socks.bo;

import io.netty.channel.Channel;

public class SocksAgentUdpConnectionInfo {
    private final int agentUdpPort;
    private final String clientSenderAssociateHost;
    private final int clientSenderAssociatePort;
    private final Channel agentTcpChannel;
    private final Channel agentUdpChannel;
    private final Channel proxyTcpChannel;
    private final String userToken;

    public SocksAgentUdpConnectionInfo(int agentUdpPort, String clientSenderAssociateHost, int clientSenderAssociatePort,
                                       String userToken,
                                       Channel agentTcpChannel, Channel agentUdpChannel,
                                       Channel proxyTcpChannel) {
        this.agentUdpPort = agentUdpPort;
        this.clientSenderAssociateHost = clientSenderAssociateHost;
        this.clientSenderAssociatePort = clientSenderAssociatePort;
        this.agentTcpChannel = agentTcpChannel;
        this.agentUdpChannel = agentUdpChannel;
        this.proxyTcpChannel = proxyTcpChannel;
        this.userToken = userToken;
    }

    public int getAgentUdpPort() {
        return agentUdpPort;
    }

    public String getClientSenderAssociateHost() {
        return clientSenderAssociateHost;
    }

    public int getClientSenderAssociatePort() {
        return clientSenderAssociatePort;
    }

    public Channel getAgentTcpChannel() {
        return agentTcpChannel;
    }

    public Channel getAgentUdpChannel() {
        return agentUdpChannel;
    }

    public Channel getProxyTcpChannel() {
        return proxyTcpChannel;
    }

    public String getUserToken() {
        return userToken;
    }
}
