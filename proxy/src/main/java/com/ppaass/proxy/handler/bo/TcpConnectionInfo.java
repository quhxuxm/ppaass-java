package com.ppaass.proxy.handler.bo;

import io.netty.channel.Channel;

public class TcpConnectionInfo {
    private final String targetHost;
    private final int targetPort;
    private final String userToken;
    private final Channel proxyTcpChannel;
    private final Channel targetTcpChannel;
    private final boolean targetTcpConnectionKeepAlive;
    private int heartBeatFailureTimes;

    public TcpConnectionInfo(String targetHost, int targetPort, String userToken, Channel proxyTcpChannel,
                             Channel targetTcpChannel, boolean targetTcpConnectionKeepAlive) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.userToken = userToken;
        this.proxyTcpChannel = proxyTcpChannel;
        this.targetTcpChannel = targetTcpChannel;
        this.targetTcpConnectionKeepAlive = targetTcpConnectionKeepAlive;
        this.heartBeatFailureTimes = 0;
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

    public int getHeartBeatFailureTimes() {
        return heartBeatFailureTimes;
    }

    public void setHeartBeatFailureTimes(int heartBeatFailureTimes) {
        this.heartBeatFailureTimes = heartBeatFailureTimes;
    }
}
