package com.ppaass.agent.handler.socks;

import com.ppaass.common.handler.PrintExceptionHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SocksAgentUdpChannelInitializer extends ChannelInitializer<NioDatagramChannel> {
    private final SocksAgentSendUdpDataToProxyHandler socksAgentSendUdpDataToProxyHandler;

    public SocksAgentUdpChannelInitializer(
            SocksAgentSendUdpDataToProxyHandler socksAgentSendUdpDataToProxyHandler) {
        this.socksAgentSendUdpDataToProxyHandler = socksAgentSendUdpDataToProxyHandler;
    }

    @Override
    protected void initChannel(NioDatagramChannel agentUdpChannel) throws Exception {
        var agentUdpChannelPipeline = agentUdpChannel.pipeline();
        agentUdpChannelPipeline.addLast(new SocksAgentUdpProtocolMessageDecoder());
        agentUdpChannelPipeline.addLast(this.socksAgentSendUdpDataToProxyHandler);
        agentUdpChannelPipeline.addLast(PrintExceptionHandler.INSTANCE);
    }
}
