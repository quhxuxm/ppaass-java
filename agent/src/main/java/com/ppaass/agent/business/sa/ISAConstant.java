package com.ppaass.agent.business.sa;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.commons.pool2.impl.GenericObjectPool;

interface ISAConstant {
    interface IAgentChannelConstant {
        AttributeKey<Channel> PROXY_CHANNEL =
                AttributeKey.valueOf("PROXY_CHANNEL");
        AttributeKey<String> TARGET_HOST =
                AttributeKey.valueOf("TARGET_HOST");
        AttributeKey<Integer> TARGET_PORT =
                AttributeKey.valueOf("TARGET_PORT");
    }

    interface IProxyChannelConstant {
        AttributeKey<GenericObjectPool<Channel>> CHANNEL_POOL =
                AttributeKey.valueOf("CHANNEL_POOL");
        AttributeKey<Boolean> CLOSED_ALREADY =
                AttributeKey.valueOf("CLOSED_ALREADY");
    }

    String LOCAL_IP_ADDRESS = "0.0.0.0";
    AttributeKey<SAUdpConnectionInfo> SOCKS_UDP_CONNECTION_INFO =
            AttributeKey.valueOf("SOCKS_UDP_CONNECTION_INFO");
}
