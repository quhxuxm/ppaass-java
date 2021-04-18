package com.ppaass.agent.business.socks.bo;

import io.netty.handler.codec.socksx.v5.Socks5AddressType;

import java.net.InetSocketAddress;

public class SocksAgentUdpProtocolMessage {
    private final InetSocketAddress udpMessageSender;
    private final InetSocketAddress udpMessageRecipient;
    private final int rsv;
    private final byte frag;
    private final Socks5AddressType addressType;
    private final String targetHost;
    private final int targetPort;
    private final byte[] data;

    public SocksAgentUdpProtocolMessage(InetSocketAddress udpMessageSender, InetSocketAddress udpMessageRecipient,
                                        int rsv,
                                        byte frag, Socks5AddressType addressType, String targetHost, int targetPort,
                                        byte[] data) {
        this.udpMessageSender = udpMessageSender;
        this.udpMessageRecipient = udpMessageRecipient;
        this.rsv = rsv;
        this.frag = frag;
        this.addressType = addressType;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.data = data;
    }

    public InetSocketAddress getUdpMessageSender() {
        return udpMessageSender;
    }

    public InetSocketAddress getUdpMessageRecipient() {
        return udpMessageRecipient;
    }

    public int getRsv() {
        return rsv;
    }

    public byte getFrag() {
        return frag;
    }

    public Socks5AddressType getAddressType() {
        return addressType;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public byte[] getData() {
        return data;
    }
}
