package com.ppaass.agent;

import com.ppaass.agent.business.socks.bo.SocksAgentTcpConnectionInfo;
import com.ppaass.agent.business.socks.bo.SocksAgentUdpConnectionInfo;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.util.concurrent.ConcurrentMap;

public interface IAgentConst {
    String LOCAL_IP_ADDRESS = "0.0.0.0";
    AttributeKey<ChannelProtocolCategory> CHANNEL_PROTOCOL_CATEGORY =
            AttributeKey.valueOf("CHANNEL_PROTOCOL_TYPE");
    String LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER";

    interface ISocksAgentConst {
        interface IAgentChannelAttr{
            AttributeKey<SocksAgentTcpConnectionInfo> TCP_CONNECTION_INFO =
                    AttributeKey.valueOf("TCP_CONNECTION_INFO");
        }
        interface IProxyChannelAttr{
            AttributeKey<Channel> AGENT_CHANNEL =
                    AttributeKey.valueOf("AGENT_CHANNEL");
            AttributeKey<GenericObjectPool<Channel>> CHANNEL_POOL =
                    AttributeKey.valueOf("CHANNEL_POOL");
        }

        AttributeKey<SocksAgentUdpConnectionInfo> SOCKS_UDP_CONNECTION_INFO =
                AttributeKey.valueOf("SOCKS_UDP_CONNECTION_INFO");
    }
}
