package com.ppaass.proxy.handler.bo;

import io.netty.channel.Channel;

public class TargetUdpInfo {
    private final String destinationAddress;
    private final int destinationPort;
    private final String sourceAddress;
    private final int sourcePort;
    private final String userToken;
    private final Channel proxyTcpChannel;
    private final Channel targetUdpChannel;

    public TargetUdpInfo(String destinationAddress, int destinationPort, String sourceAddress, int sourcePort,
                         String userToken, Channel proxyTcpChannel, Channel targetUdpChannel) {
        this.destinationAddress = destinationAddress;
        this.destinationPort = destinationPort;
        this.userToken = userToken;
        this.proxyTcpChannel = proxyTcpChannel;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
        this.targetUdpChannel = targetUdpChannel;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public String getUserToken() {
        return userToken;
    }

    public Channel getProxyTcpChannel() {
        return proxyTcpChannel;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public Channel getTargetUdpChannel() {
        return targetUdpChannel;
    }
}
