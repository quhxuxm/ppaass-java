package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.constant.ICommonConstant;
import com.ppaass.common.handler.AgentMessageEncoder;
import com.ppaass.common.handler.PrintExceptionHandler;
import com.ppaass.common.handler.ProxyMessageDecoder;
import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.Lz4FrameDecoder;
import io.netty.handler.codec.compression.Lz4FrameEncoder;
import org.springframework.stereotype.Service;

@Service
class SAProxyTcpChannelInitializer extends ChannelInitializer<Channel> {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final AgentConfiguration agentConfiguration;
    private final SAReceiveProxyDataHandler saReceiveProxyDataHandler;

    public SAProxyTcpChannelInitializer(
            AgentConfiguration agentConfiguration,
            SAReceiveProxyDataHandler saReceiveProxyDataHandler) {
        this.agentConfiguration = agentConfiguration;
        this.saReceiveProxyDataHandler = saReceiveProxyDataHandler;
    }

    @Override
    protected void initChannel(Channel proxyChannel) throws Exception {
        logger.info(
                () -> "Proxy channel created, proxy channel = " + proxyChannel.id().asLongText());
        var proxyChannelPipeline = proxyChannel.pipeline();
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameDecoder());
        }
        proxyChannelPipeline.addLast(
                new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER,
                        0, ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
        proxyChannelPipeline.addLast(new ProxyMessageDecoder());
        proxyChannelPipeline.addLast(this.saReceiveProxyDataHandler);
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameEncoder());
        }
        proxyChannelPipeline.addLast(new LengthFieldPrepender(ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
        proxyChannelPipeline.addLast(new AgentMessageEncoder());
        proxyChannelPipeline.addLast(PrintExceptionHandler.INSTANCE);
    }
}
