package com.ppaass.agent.handler.socks;

import com.ppaass.agent.handler.socks.bo.SocksAgentTcpConnectionInfo;
import com.ppaass.agent.handler.socks.bo.SocksAgentUdpConnectionInfo;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;

import java.util.concurrent.ConcurrentMap;

interface ISocksAgentConst {
    interface IAgentChannelAttr{
        AttributeKey<SocksAgentTcpConnectionInfo> TCP_CONNECTION_INFO =
                AttributeKey.valueOf("TCP_CONNECTION_INFO");
    }
    interface IProxyChannelAttr{
        AttributeKey<ConcurrentMap<String, Channel>> AGENT_CHANNELS =
                AttributeKey.valueOf("AGENT_CHANNELS");
        AttributeKey<ChannelPool> CHANNEL_POOL =
                AttributeKey.valueOf("CHANNEL_POOL");
    }

    AttributeKey<SocksAgentUdpConnectionInfo> SOCKS_UDP_CONNECTION_INFO =
            AttributeKey.valueOf("SOCKS_UDP_CONNECTION_INFO");
}
