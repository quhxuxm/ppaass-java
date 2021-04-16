package com.ppaass.proxy;

import com.ppaass.proxy.handler.bo.TargetTcpInfo;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.concurrent.ConcurrentMap;

public interface IProxyConstant {
    String LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER";
    String TARGET_CHANNEL_KEY_FORMAT = "%s:%s";

    interface ITargetChannelAttr {
        AttributeKey<TargetTcpInfo> TCP_INFO =
                AttributeKey.valueOf("TCP_INFO");
    }

    interface IProxyChannelAttr {
        AttributeKey<ConcurrentMap<String, Channel>> TARGET_CHANNELS =
                AttributeKey.valueOf("TARGET_CHANNELS");
    }
}
