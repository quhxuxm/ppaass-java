package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.handler.PrintExceptionHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SocksAgentUdpProxyChannelInitializer extends ChannelInitializer<NioDatagramChannel> {
    private final PrintExceptionHandler printExceptionHandler;
    private final AgentConfiguration agentConfiguration;

    public SocksAgentUdpProxyChannelInitializer(PrintExceptionHandler printExceptionHandler,
                                                AgentConfiguration agentConfiguration) {
        this.printExceptionHandler = printExceptionHandler;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    protected void initChannel(NioDatagramChannel agentUdpChannel) throws Exception {
        var agentUdpChannelPipeline = agentUdpChannel.pipeline();
        agentUdpChannelPipeline.addLast(new SocksAgentUdpMessageDecoder());
        agentUdpChannelPipeline.addLast(socksForwardUdpMessageToProxyTcpChannelHandler);
        agentUdpChannelPipeline.addLast(printExceptionHandler);
    }
}
