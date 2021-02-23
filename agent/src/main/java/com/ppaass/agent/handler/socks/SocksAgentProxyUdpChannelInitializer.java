package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.handler.PrintExceptionHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SocksAgentProxyUdpChannelInitializer extends ChannelInitializer<NioDatagramChannel> {
    private final PrintExceptionHandler printExceptionHandler;
    private final AgentConfiguration agentConfiguration;
    private final SocksAgentA2PUdpChannelHandler socksAgentA2PUdpChannelHandler;

    public SocksAgentProxyUdpChannelInitializer(PrintExceptionHandler printExceptionHandler,
                                                AgentConfiguration agentConfiguration,
                                                SocksAgentA2PUdpChannelHandler socksAgentA2PUdpChannelHandler) {
        this.printExceptionHandler = printExceptionHandler;
        this.agentConfiguration = agentConfiguration;
        this.socksAgentA2PUdpChannelHandler = socksAgentA2PUdpChannelHandler;
    }

    @Override
    protected void initChannel(NioDatagramChannel agentUdpChannel) throws Exception {
        var agentUdpChannelPipeline = agentUdpChannel.pipeline();
        agentUdpChannelPipeline.addLast(new SocksAgentUdpMessageDecoder());
        agentUdpChannelPipeline.addLast(this.socksAgentA2PUdpChannelHandler);
        agentUdpChannelPipeline.addLast(printExceptionHandler);
    }
}
