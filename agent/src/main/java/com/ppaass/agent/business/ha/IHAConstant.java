package com.ppaass.agent.business.ha;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.util.concurrent.ConcurrentMap;

interface IHAConstant {
    interface IAgentChannelConstant{
        AttributeKey<HAConnectionInfo> HTTP_CONNECTION_INFO =
                AttributeKey.valueOf("HTTP_CONNECTION_INFO");
    }
    interface IProxyChannelConstant{
        AttributeKey<ConcurrentMap<String, Channel>> AGENT_CHANNELS =
                AttributeKey.valueOf("AGENT_CHANNELS");
        AttributeKey<Channel> AGENT_CHANNEL_TO_SEND_PURE_DATA =
                AttributeKey.valueOf("AGENT_CHANNEL_TO_SEND_PURE_DATA");
        AttributeKey<GenericObjectPool<Channel>> CHANNEL_POOL =
                AttributeKey.valueOf("CHANNEL_POOL");
        AttributeKey<Boolean> CLOSED_ALREADY =
                AttributeKey.valueOf("CLOSED_ALREADY");
    }

    String CONNECTION_ESTABLISHED = "Connection Established";
}
