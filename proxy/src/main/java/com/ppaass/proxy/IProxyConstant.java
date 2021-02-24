package com.ppaass.proxy;

import com.ppaass.proxy.handler.bo.TcpConnectionInfo;
import com.ppaass.proxy.handler.bo.UdpConnectionInfo;
import io.netty.util.AttributeKey;

public interface IProxyConstant {
    String LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER";
    AttributeKey<TcpConnectionInfo> TCP_CONNECTION_INFO =
            AttributeKey.valueOf("TCP_CONNECTION_INFO");
    /**
     * The attribute key of udp connection information
     */
    AttributeKey<UdpConnectionInfo> UDP_CONNECTION_INFO =
            AttributeKey.valueOf("UDP_CONNECTION_INFO");
}
