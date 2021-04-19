package com.ppaass.agent;

import io.netty.util.AttributeKey;

public interface IAgentConst {
    String LOCAL_IP_ADDRESS = "0.0.0.0";
    AttributeKey<ChannelProtocolCategory> CHANNEL_PROTOCOL_CATEGORY =
            AttributeKey.valueOf("CHANNEL_PROTOCOL_TYPE");
    String LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER";
}
