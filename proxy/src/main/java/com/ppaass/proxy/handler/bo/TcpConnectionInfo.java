package com.ppaass.proxy.handler.bo;

import io.netty.channel.Channel;

public class TcpConnectionInfo {
    private final String targetHost;
    private final int targetPort;
    private final String userToken;
    private final Channel proxyTcpChannel;
    private final Channel targetTcpChannel;
    private final boolean targetTcpConnectionKeepAlive;
    private boolean heartbeatPending;

    public TcpConnectionInfo(String targetHost, int targetPort, String userToken, Channel proxyTcpChannel,
                             Channel targetTcpChannel, boolean targetTcpConnectionKeepAlive) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.userToken = userToken;
        this.proxyTcpChannel = proxyTcpChannel;
        this.targetTcpChannel = targetTcpChannel;
        this.targetTcpConnectionKeepAlive = targetTcpConnectionKeepAlive;
        this.heartbeatPending = false;
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

    public Channel getTargetTcpChannel() {
        return targetTcpChannel;
    }

    public boolean isTargetTcpConnectionKeepAlive() {
        return targetTcpConnectionKeepAlive;
    }

    public boolean isHeartbeatPending() {
        return heartbeatPending;
    }

    public void setHeartbeatPending(boolean heartbeatPending) {
        this.heartbeatPending = heartbeatPending;
    }
}
