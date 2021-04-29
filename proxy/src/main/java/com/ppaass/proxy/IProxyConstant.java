package com.ppaass.proxy;

import com.ppaass.proxy.handler.bo.TargetTcpInfo;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public interface IProxyConstant {
    String LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER";

    interface ITargetChannelAttr {
        AttributeKey<TargetTcpInfo> TCP_INFO =
                AttributeKey.valueOf("TCP_INFO");
    }

    interface IProxyChannelAttr {
        AttributeKey<Channel> TARGET_CHANNEL =
                AttributeKey.valueOf("TARGET_CHANNEL");
    }
}
