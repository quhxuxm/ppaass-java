package com.ppaass.agent;

import com.ppaass.agent.business.ChannelWrapper;
import io.netty.util.AttributeKey;

import java.util.concurrent.ConcurrentMap;

public interface IAgentConst {
    String LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER";
    AttributeKey<ChannelProtocolCategory> CHANNEL_PROTOCOL_CATEGORY =
            AttributeKey.valueOf("CHANNEL_PROTOCOL_TYPE");
    interface IProxyChannelAttr{
        AttributeKey<ConcurrentMap<String, ChannelWrapper>> AGENT_CHANNELS =
                AttributeKey.valueOf("AGENT_CHANNELS");
    }
}
