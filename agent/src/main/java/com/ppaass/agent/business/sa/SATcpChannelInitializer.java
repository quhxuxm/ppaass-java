package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.handler.AgentMessageEncoder;
import com.ppaass.common.handler.PrintExceptionHandler;
import com.ppaass.common.handler.ProxyMessageDecoder;
import com.ppaass.common.log.PpaassLogger;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.Lz4FrameDecoder;
import io.netty.handler.codec.compression.Lz4FrameEncoder;
import org.springframework.stereotype.Service;

@Service
class SATcpChannelInitializer extends ChannelInitializer<Channel> {
    private final AgentConfiguration agentConfiguration;
    private final SAReceiveProxyDataHandler SAReceiveProxyDataHandler;

    public SATcpChannelInitializer(
            AgentConfiguration agentConfiguration,
            SAReceiveProxyDataHandler SAReceiveProxyDataHandler) {
        this.agentConfiguration = agentConfiguration;
        this.SAReceiveProxyDataHandler = SAReceiveProxyDataHandler;
    }

    @Override
    protected void initChannel(Channel proxyChannel) throws Exception {
        PpaassLogger.INSTANCE.info(
                () -> "Proxy channel created, proxy channel = " + proxyChannel.id().asLongText());
        var proxyChannelPipeline = proxyChannel.pipeline();
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameDecoder());
        }
        proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        proxyChannelPipeline.addLast(new ProxyMessageDecoder(agentConfiguration.getAgentPrivateKey()));
        proxyChannelPipeline.addLast(this.SAReceiveProxyDataHandler);
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameEncoder());
        }
        proxyChannelPipeline.addLast(new LengthFieldPrepender(4));
        proxyChannelPipeline.addLast(new AgentMessageEncoder(agentConfiguration.getProxyPublicKey()));
        proxyChannelPipeline.addLast(PrintExceptionHandler.INSTANCE);
    }
}
