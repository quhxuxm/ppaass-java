package com.ppaass.agent.business;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.ChannelProtocolCategory;
import com.ppaass.agent.IAgentConst;
import com.ppaass.agent.business.ha.HAEntryHandler;
import com.ppaass.agent.business.sa.SAEntryHandler;
import com.ppaass.common.handler.ChannelCleanupHandler;
import com.ppaass.common.log.PpaassLogger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class DetectProtocolHandler extends ChannelInboundHandlerAdapter {
    private static final AttributeKey<ChannelProtocolCategory> CHANNEL_PROTOCOL_CATEGORY =
            AttributeKey.valueOf("CHANNEL_PROTOCOL_TYPE");
    private final SAEntryHandler saEntryHandler;
    private final AgentConfiguration agentConfiguration;
    private final HAEntryHandler haEntryHandler;

    public DetectProtocolHandler(
            SAEntryHandler saEntryHandler,
            HAEntryHandler haEntryHandler,
            AgentConfiguration agentConfiguration
    ) {
        this.saEntryHandler = saEntryHandler;
        this.haEntryHandler = haEntryHandler;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void channelRead(ChannelHandlerContext agentChannelContext, Object msg) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var channelProtocolType = agentChannel.attr(CHANNEL_PROTOCOL_CATEGORY).get();
        if (channelProtocolType != null) {
            PpaassLogger.INSTANCE
                    .debug(() -> "Incoming request protocol is: {}, agent channel = {}",
                            () -> new Object[]{channelProtocolType,
                                    agentChannel.id().asLongText()});
            agentChannelContext.fireChannelRead(msg);
            return;
        }
        var messageBuf = (ByteBuf) msg;
        var readerIndex = messageBuf.readerIndex();
        if (messageBuf.writerIndex() == readerIndex) {
            PpaassLogger.INSTANCE
                    .debug(
                            () -> "Incoming request reader index is the same as writer index, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()});
            agentChannel.close();
            return;
        }
        var protocolVersionByte = messageBuf.getByte(readerIndex);
        var agentChannelPipeline = agentChannelContext.pipeline();
        if (SocksVersion.SOCKS4a.byteValue() == protocolVersionByte ||
                SocksVersion.SOCKS5.byteValue() == protocolVersionByte) {
            PpaassLogger.INSTANCE
                    .debug(
                            () -> "Incoming request is a socks request, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()});
            agentChannel.attr(CHANNEL_PROTOCOL_CATEGORY)
                    .set(ChannelProtocolCategory.SOCKS);
            agentChannelPipeline
                    .addBefore(IAgentConst.LAST_INBOUND_HANDLER, SocksPortUnificationServerHandler.class.getName(),
                            new SocksPortUnificationServerHandler());
            agentChannelPipeline.addBefore(IAgentConst.LAST_INBOUND_HANDLER, SAEntryHandler.class.getName(),
                    saEntryHandler);
            agentChannelPipeline.addBefore(IAgentConst.LAST_INBOUND_HANDLER, IdleStateHandler.class.getName(),
                    new IdleStateHandler(0,
                            0,
                            this.agentConfiguration.getAgentChannelAllIdleSeconds()));
            agentChannelPipeline
                    .addBefore(IAgentConst.LAST_INBOUND_HANDLER,
                            ChannelCleanupHandler.class.getName(),
                            ChannelCleanupHandler.INSTANCE);
            agentChannelPipeline.remove(this);
            agentChannelContext.fireChannelRead(messageBuf);
            return;
        }
        PpaassLogger.INSTANCE
                .debug(
                        () -> "Incoming request is a http request, agent channel = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText()});
        agentChannel.attr(CHANNEL_PROTOCOL_CATEGORY)
                .set(ChannelProtocolCategory.HTTP);
        agentChannelPipeline.addLast(HttpServerCodec.class.getName(), new HttpServerCodec());
        agentChannelPipeline.addLast(HttpObjectAggregator.class.getName(),
                new HttpObjectAggregator(Integer.MAX_VALUE, true));
        agentChannelPipeline.addLast(this.haEntryHandler);
        agentChannelPipeline.addBefore(IAgentConst.LAST_INBOUND_HANDLER, IdleStateHandler.class.getName(),
                new IdleStateHandler(0,
                        0,
                        this.agentConfiguration.getAgentChannelAllIdleSeconds()));
        agentChannelPipeline
                .addBefore(IAgentConst.LAST_INBOUND_HANDLER,
                        ChannelCleanupHandler.class.getName(),
                        ChannelCleanupHandler.INSTANCE);
        agentChannelPipeline.remove(this);
        agentChannelContext.fireChannelRead(messageBuf);
    }
}
