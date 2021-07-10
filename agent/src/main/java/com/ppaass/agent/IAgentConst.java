package com.ppaass.agent;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * The const for the agent.
 */
public interface IAgentConst {
    /**
     * The latest channel handler.
     */
    String LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER";
    /**
     * The channel attribute of the protocol type.
     */
    AttributeKey<ChannelProtocolCategory> CHANNEL_PROTOCOL_CATEGORY =
            AttributeKey.valueOf("CHANNEL_PROTOCOL_TYPE");

    interface IProxyChannelAttr {
        /**
         * The attribute of agent channel in proxy channel
         */
        AttributeKey<Channel> AGENT_CHANNEL =
                AttributeKey.valueOf("AGENT_CHANNEL");
    }
}
