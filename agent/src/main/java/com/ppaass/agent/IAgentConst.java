package com.ppaass.agent;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public interface IAgentConst {
    String LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER";
    AttributeKey<ChannelProtocolCategory> CHANNEL_PROTOCOL_CATEGORY =
            AttributeKey.valueOf("CHANNEL_PROTOCOL_TYPE");
    interface IProxyChannelAttr{
        AttributeKey<Channel> AGENT_CHANNEL=
                AttributeKey.valueOf("AGENT_CHANNEL");
    }
}
