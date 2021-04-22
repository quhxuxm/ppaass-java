package com.ppaass.agent.business.sa;

import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.NetUtil;

public class SAUtil {
    public static final SAUtil INSTANCE = new SAUtil();

    private SAUtil() {
    }

    public Socks5AddressType parseAddrType(String targetHost) {
        if (NetUtil.isValidIpV4Address(targetHost)) {
            return Socks5AddressType.IPv4;
        }
        if (NetUtil.isValidIpV6Address(targetHost)) {
            return Socks5AddressType.IPv6;
        }
        return Socks5AddressType.DOMAIN;
    }
}
