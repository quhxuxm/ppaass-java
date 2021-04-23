package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.business.ClearClosedAgentChannelHandler;
import com.ppaass.common.constant.ICommonConstant;
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
class SAProxyTcpChannelInitializer extends ChannelInitializer<Channel> {
    private final AgentConfiguration agentConfiguration;
    private final SAReceiveProxyDataHandler saReceiveProxyDataHandler;
    private final ClearClosedAgentChannelHandler clearClosedAgentChannelHandler;

    public SAProxyTcpChannelInitializer(
            AgentConfiguration agentConfiguration,
            SAReceiveProxyDataHandler saReceiveProxyDataHandler,
            ClearClosedAgentChannelHandler clearClosedAgentChannelHandler) {
        this.agentConfiguration = agentConfiguration;
        this.saReceiveProxyDataHandler = saReceiveProxyDataHandler;
        this.clearClosedAgentChannelHandler = clearClosedAgentChannelHandler;
    }

    @Override
    protected void initChannel(Channel proxyChannel) throws Exception {
        PpaassLogger.INSTANCE.info(
                () -> "Proxy channel created, proxy channel = " + proxyChannel.id().asLongText());
        var proxyChannelPipeline = proxyChannel.pipeline();
        proxyChannelPipeline.addLast(clearClosedAgentChannelHandler);
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameDecoder());
        }
        proxyChannelPipeline.addLast(
                new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER,
                        0, ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
        proxyChannelPipeline.addLast(new ProxyMessageDecoder(agentConfiguration.getAgentPrivateKey()));
        proxyChannelPipeline.addLast(this.saReceiveProxyDataHandler);
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameEncoder());
        }
        proxyChannelPipeline.addLast(new LengthFieldPrepender(ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
        proxyChannelPipeline.addLast(new AgentMessageEncoder(agentConfiguration.getProxyPublicKey()));
        proxyChannelPipeline.addLast(PrintExceptionHandler.INSTANCE);
    }
}
