package com.ppaass.agent.business.ha;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

interface IHAConstant {
    interface IAgentChannelConstant {
        AttributeKey<HAConnectionInfo> HTTP_CONNECTION_INFO =
                AttributeKey.valueOf("HTTP_CONNECTION_INFO");
    }

    interface IProxyChannelConstant {
        AttributeKey<Channel> AGENT_CHANNEL_TO_SEND_PURE_DATA =
                AttributeKey.valueOf("AGENT_CHANNEL_TO_SEND_PURE_DATA");
    }

    String CONNECTION_ESTABLISHED = "Connection Established";
}
