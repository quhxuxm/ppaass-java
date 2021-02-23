package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.handler.AgentMessageEncoder;
import com.ppaass.common.handler.PrintExceptionHandler;
import com.ppaass.common.handler.ProxyMessageDecoder;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ServerChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.Lz4FrameDecoder;
import io.netty.handler.codec.compression.Lz4FrameEncoder;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SocksAgentTcpProxyChannelInitializer extends ChannelInitializer<ServerChannel> {
    private final PrintExceptionHandler printExceptionHandler;
    private final AgentConfiguration agentConfiguration;

    public SocksAgentTcpProxyChannelInitializer(PrintExceptionHandler printExceptionHandler,
                                                AgentConfiguration agentConfiguration) {
        this.printExceptionHandler = printExceptionHandler;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    protected void initChannel(ServerChannel proxyChannel) throws Exception {
        var proxyChannelPipeline = proxyChannel.pipeline();
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameDecoder());
        }
        proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        proxyChannelPipeline.addLast(new ProxyMessageDecoder(agentConfiguration.getAgentPrivateKey()));
        proxyChannelPipeline.addLast(socksProxyToAgentTcpChannelHandler);
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameEncoder());
        }
        proxyChannelPipeline.addLast(new LengthFieldPrepender(4));
        proxyChannelPipeline.addLast(new AgentMessageEncoder(agentConfiguration.getProxyPublicKey()));
        proxyChannelPipeline.addLast(this.printExceptionHandler);
    }
}
