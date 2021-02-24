package com.ppaass.proxy.handler.bo;

import io.netty.channel.Channel;

public class UdpConnectionInfo {
    private final String targetHost;
    private final int targetPort;
    private final String userToken;
    private final Channel proxyTcpChannel;
    private final Channel targetUdpChannel;

    public UdpConnectionInfo(String targetHost, int targetPort, String userToken, Channel proxyTcpChannel,
                             Channel targetUdpChannel) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.userToken = userToken;
        this.proxyTcpChannel = proxyTcpChannel;
        this.targetUdpChannel = targetUdpChannel;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public String getUserToken() {
        return userToken;
    }

    public Channel getProxyTcpChannel() {
        return proxyTcpChannel;
    }

    public Channel getTargetUdpChannel() {
        return targetUdpChannel;
    }
}
