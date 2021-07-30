package com.ppaass.agent.business.sa;

import com.ppaass.common.handler.PrintExceptionHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SAUdpChannelInitializer extends ChannelInitializer<NioDatagramChannel> {
    private final SASendUdpDataToProxyHandler SASendUdpDataToProxyHandler;

    public SAUdpChannelInitializer(
            SASendUdpDataToProxyHandler SASendUdpDataToProxyHandler) {
        this.SASendUdpDataToProxyHandler = SASendUdpDataToProxyHandler;
    }

    @Override
    protected void initChannel(NioDatagramChannel agentUdpChannel) throws Exception {
        var agentUdpChannelPipeline = agentUdpChannel.pipeline();
        agentUdpChannelPipeline.addLast(new SAUdpProtocolMessageDecoder());
        agentUdpChannelPipeline.addLast(this.SASendUdpDataToProxyHandler);
        agentUdpChannelPipeline.addLast(PrintExceptionHandler.INSTANCE);
    }
}
