package com.ppaass.common.message;

public class UdpTransferMessageContent {
    public enum AddrType {
        IPV4, IPV6, DOMAIN
    }

    private String originalSourceAddress;
    private int originalSourcePort;
    private String originalDestinationAddress;
    private int originalDestinationPort;
    private byte[] data;
    private AddrType originalAddrType;

    public void setOriginalDestinationAddress(String originalDestinationAddress) {
        this.originalDestinationAddress = originalDestinationAddress;
    }

    public String getOriginalSourceAddress() {
        return originalSourceAddress;
    }

    public int getOriginalSourcePort() {
        return originalSourcePort;
    }

    public void setOriginalSourcePort(int originalSourcePort) {
        this.originalSourcePort = originalSourcePort;
    }

    public void setOriginalSourceAddress(String originalSourceAddress) {
        this.originalSourceAddress = originalSourceAddress;
    }

    public String getOriginalDestinationAddress() {
        return originalDestinationAddress;
    }

    public int getOriginalDestinationPort() {
        return originalDestinationPort;
    }

    public void setOriginalDestinationPort(int originalDestinationPort) {
        this.originalDestinationPort = originalDestinationPort;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setOriginalAddrType(AddrType originalAddrType) {
        this.originalAddrType = originalAddrType;
    }

    public AddrType getOriginalAddrType() {
        return originalAddrType;
    }
}
