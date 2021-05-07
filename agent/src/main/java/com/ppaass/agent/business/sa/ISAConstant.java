package com.ppaass.agent.business.sa;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

interface ISAConstant {
    interface IAgentChannelConstant {
        AttributeKey<Channel> PROXY_CHANNEL =
                AttributeKey.valueOf("PROXY_CHANNEL");
        AttributeKey<String> TARGET_HOST =
                AttributeKey.valueOf("TARGET_HOST");
        AttributeKey<Integer> TARGET_PORT =
                AttributeKey.valueOf("TARGET_PORT");
    }

    String LOCAL_IP_ADDRESS = "0.0.0.0";
    AttributeKey<SAUdpConnectionInfo> SOCKS_UDP_CONNECTION_INFO =
            AttributeKey.valueOf("SOCKS_UDP_CONNECTION_INFO");
}
