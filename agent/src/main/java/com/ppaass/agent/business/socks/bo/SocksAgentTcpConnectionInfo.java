package com.ppaass.agent.business.socks.bo;

import io.netty.channel.Channel;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;

public class SocksAgentTcpConnectionInfo {
    private final String targetHost;
    private final int targetPort;
    private final Socks5AddressType targetAddressType;
    private final String userToken;
    private final Channel agentTcpChannel;
    private final Channel proxyTcpChannel;
    private String targetChannelId;

    public SocksAgentTcpConnectionInfo(String targetHost, int targetPort,
                                       Socks5AddressType targetAddressType, String userToken,
                                       Channel agentTcpChannel,
                                       Channel proxyTcpChannel) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.targetAddressType = targetAddressType;
        this.userToken = userToken;
        this.agentTcpChannel = agentTcpChannel;
        this.proxyTcpChannel = proxyTcpChannel;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public Socks5AddressType getTargetAddressType() {
        return targetAddressType;
    }

    public String getUserToken() {
        return userToken;
    }

    public Channel getAgentTcpChannel() {
        return agentTcpChannel;
    }

    public Channel getProxyTcpChannel() {
        return proxyTcpChannel;
    }

    public void setTargetChannelId(String targetChannelId) {
        this.targetChannelId = targetChannelId;
    }

    public String getTargetChannelId() {
        return targetChannelId;
    }
}
