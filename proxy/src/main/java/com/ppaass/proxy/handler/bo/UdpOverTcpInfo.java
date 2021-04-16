package com.ppaass.proxy.handler.bo;

import io.netty.channel.Channel;

public class UdpOverTcpInfo {
    private final Channel proxyTcpChannel;
    private final Channel targetUdpChannel;

    public UdpOverTcpInfo(Channel proxyTcpChannel, Channel targetUdpChannel) {
        this.proxyTcpChannel = proxyTcpChannel;
        this.targetUdpChannel = targetUdpChannel;
    }

    public Channel getProxyTcpChannel() {
        return proxyTcpChannel;
    }

    public Channel getTargetUdpChannel() {
        return targetUdpChannel;
    }
}
