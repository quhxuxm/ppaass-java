package com.ppaass.agent.handler.socks;

import com.ppaass.agent.handler.socks.bo.SocksAgentTcpConnectionInfo;
import com.ppaass.agent.handler.socks.bo.SocksAgentUdpConnectionInfo;
import io.netty.util.AttributeKey;

interface ISocksAgentConst {
    AttributeKey<SocksAgentTcpConnectionInfo> SOCKS_TCP_CONNECTION_INFO =
            AttributeKey.valueOf("SOCKS_TCP_CONNECTION_INFO");
    AttributeKey<SocksAgentUdpConnectionInfo> SOCKS_UDP_CONNECTION_INFO =
            AttributeKey.valueOf("SOCKS_UDP_CONNECTION_INFO");
}
