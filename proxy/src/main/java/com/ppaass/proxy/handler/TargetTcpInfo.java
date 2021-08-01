package com.ppaass.proxy.handler;

import io.netty.channel.Channel;

public class TargetTcpInfo {
    private final String agentInstanceId;
    private final String agentChannelId;
    private final String targetChannelId;
    private final String sourceHost;
    private final int sourcePort;
    private final String targetHost;
    private final int targetPort;
    private final String userToken;
    private final Channel proxyTcpChannel;
    private final Channel targetTcpChannel;

    public TargetTcpInfo(String agentInstanceId, String sourceHost, int sourcePort, String targetHost,
                         int targetPort, String userToken,
                         String agentChannelId,
                         String targetChannelId,
                         Channel proxyTcpChannel,
                         Channel targetTcpChannel) {
        this.agentInstanceId = agentInstanceId;
        this.sourceHost = sourceHost;
        this.sourcePort = sourcePort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.userToken = userToken;
        this.proxyTcpChannel = proxyTcpChannel;
        this.targetTcpChannel = targetTcpChannel;
        this.agentChannelId = agentChannelId;
        this.targetChannelId = targetChannelId;
    }

    public String getAgentInstanceId() {
        return agentInstanceId;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public String getSourceHost() {
        return sourceHost;
    }

    public String getUserToken() {
        return userToken;
    }

    public Channel getProxyTcpChannel() {
        return proxyTcpChannel;
    }

    public Channel getTargetTcpChannel() {
        return targetTcpChannel;
    }

    public String getAgentChannelId() {
        return agentChannelId;
    }

    public String getTargetChannelId() {
        return targetChannelId;
    }

    @Override
    public String toString() {
        return "TargetTcpInfo{" +
                "agentInstanceId='" + agentInstanceId + '\'' +
                ", agentChannelId='" + agentChannelId + '\'' +
                ", targetChannelId='" + targetChannelId + '\'' +
                ", sourceHost='" + sourceHost + '\'' +
                ", sourcePort=" + sourcePort +
                ", targetHost='" + targetHost + '\'' +
                ", targetPort=" + targetPort +
                ", userToken='" + userToken + '\'' +
                ", proxyTcpChannel=" + proxyTcpChannel +
                ", targetTcpChannel=" + targetTcpChannel +
                '}';
    }
}
