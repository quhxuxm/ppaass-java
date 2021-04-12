package com.ppaass.common.message;

public class UdpMessageContent {
    public enum AddrType {
        IPV4, IPV6, DOMAIN
    }

    private String sourceAddress;
    private int sourcePort;
    private String destinationAddress;
    private int destinationPort;
    private byte[] data;
    private AddrType addrType;

    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setAddrType(AddrType addrType) {
        this.addrType = addrType;
    }

    public AddrType getAddrType() {
        return addrType;
    }
}
