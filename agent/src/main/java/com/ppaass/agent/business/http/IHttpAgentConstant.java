package com.ppaass.agent.business.http;

import com.ppaass.agent.business.http.bo.HttpConnectionInfo;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.commons.pool2.impl.GenericObjectPool;

interface IHttpAgentConstant {
    interface IAgentChannelConstant{
        AttributeKey<HttpConnectionInfo> HTTP_CONNECTION_INFO =
                AttributeKey.valueOf("HTTP_CONNECTION_INFO");
    }
    interface IProxyChannelConstant{
        AttributeKey<HttpConnectionInfo> HTTP_CONNECTION_INFO =
                AttributeKey.valueOf("HTTP_CONNECTION_INFO");
        AttributeKey<GenericObjectPool<Channel>> CHANNEL_POOL =
                AttributeKey.valueOf("CHANNEL_POOL");
    }

    String CONNECTION_ESTABLISHED = "Connection Established";
}
