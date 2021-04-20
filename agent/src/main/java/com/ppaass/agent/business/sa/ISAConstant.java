package com.ppaass.agent.business.sa;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.commons.pool2.impl.GenericObjectPool;

interface ISAConstant {
    interface IAgentChannelConstant {
        AttributeKey<SATcpConnectionInfo> TCP_CONNECTION_INFO =
                AttributeKey.valueOf("TCP_CONNECTION_INFO");
    }

    interface IProxyChannelConstant {
        AttributeKey<Channel> AGENT_CHANNEL =
                AttributeKey.valueOf("AGENT_CHANNEL");
        AttributeKey<GenericObjectPool<Channel>> CHANNEL_POOL =
                AttributeKey.valueOf("CHANNEL_POOL");
        AttributeKey<Boolean> CLOSED_ALREADY =
                AttributeKey.valueOf("CLOSED_ALREADY");
    }

    String LOCAL_IP_ADDRESS = "0.0.0.0";
    AttributeKey<SAUdpConnectionInfo> SOCKS_UDP_CONNECTION_INFO =
            AttributeKey.valueOf("SOCKS_UDP_CONNECTION_INFO");
}
