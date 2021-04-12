package com.ppaass.proxy.handler.bo;

import com.ppaass.common.message.UdpMessageContent;
import io.netty.channel.Channel;

public class UdpConnectionInfo {
    private final String destinationAddress;
    private final int destinationPort;
    private final String sourceAddress;
    private final UdpMessageContent.AddrType addrType;
    private final int sourcePort;
    private final String userToken;
    private final Channel proxyTcpChannel;
    private final Channel targetUdpChannel;

    public UdpConnectionInfo(String destinationAddress, int destinationPort, String sourceAddress, int sourcePort,
                             UdpMessageContent.AddrType addrType, String userToken, Channel proxyTcpChannel,
                             Channel targetUdpChannel) {
        this.destinationAddress = destinationAddress;
        this.destinationPort = destinationPort;
        this.userToken = userToken;
        this.proxyTcpChannel = proxyTcpChannel;
        this.targetUdpChannel = targetUdpChannel;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
        this.addrType = addrType;
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

    public Channel getTargetUdpChannel() {
        return targetUdpChannel;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public UdpMessageContent.AddrType getAddrType() {
        return addrType;
    }
}
