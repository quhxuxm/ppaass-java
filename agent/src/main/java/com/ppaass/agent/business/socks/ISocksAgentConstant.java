package com.ppaass.agent.business.socks;

import com.ppaass.agent.business.socks.bo.SocksAgentTcpConnectionInfo;
import com.ppaass.agent.business.socks.bo.SocksAgentUdpConnectionInfo;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.commons.pool2.impl.GenericObjectPool;

interface ISocksAgentConstant {
        interface IAgentChannelConstant {
            AttributeKey<SocksAgentTcpConnectionInfo> TCP_CONNECTION_INFO =
                    AttributeKey.valueOf("TCP_CONNECTION_INFO");
        }
        interface IProxyChannelConstant {
            AttributeKey<Channel> AGENT_CHANNEL =
                    AttributeKey.valueOf("AGENT_CHANNEL");
            AttributeKey<GenericObjectPool<Channel>> CHANNEL_POOL =
                    AttributeKey.valueOf("CHANNEL_POOL");
        }

        AttributeKey<SocksAgentUdpConnectionInfo> SOCKS_UDP_CONNECTION_INFO =
                AttributeKey.valueOf("SOCKS_UDP_CONNECTION_INFO");
    }
