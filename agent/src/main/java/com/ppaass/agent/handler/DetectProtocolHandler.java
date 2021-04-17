package com.ppaass.agent.handler;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.ChannelProtocolCategory;
import com.ppaass.agent.IAgentConst;
import com.ppaass.agent.handler.socks.SocksAgentCleanupInactiveAgentChannelHandler;
import com.ppaass.agent.handler.socks.SocksAgentEntryHandler;
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
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class DetectProtocolHandler extends ChannelInboundHandlerAdapter {
    private final SocksAgentEntryHandler socksAgentEntryHandler;
    private final AgentConfiguration agentConfiguration;
    private final SocksAgentCleanupInactiveAgentChannelHandler socksAgentCleanupInactiveAgentChannelHandler;
//    private final HttpAgentProtocolHandler httpAgentProtocolHandler;

    public DetectProtocolHandler(SocksAgentEntryHandler socksAgentEntryHandler,
                                 AgentConfiguration agentConfiguration,
                                 SocksAgentCleanupInactiveAgentChannelHandler socksAgentCleanupInactiveAgentChannelHandler) {
        this.socksAgentEntryHandler = socksAgentEntryHandler;
//        this.httpAgentProtocolHandler = httpAgentProtocolHandler;
        this.agentConfiguration = agentConfiguration;
        this.socksAgentCleanupInactiveAgentChannelHandler = socksAgentCleanupInactiveAgentChannelHandler;
    }

    @Override
    public void channelRead(ChannelHandlerContext agentChannelContext, Object msg) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var channelProtocolType = agentChannel.attr(IAgentConst.CHANNEL_PROTOCOL_CATEGORY).get();
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
            agentChannel.attr(IAgentConst.CHANNEL_PROTOCOL_CATEGORY)
                    .setIfAbsent(ChannelProtocolCategory.SOCKS);
            agentChannelPipeline
                    .addBefore(IAgentConst.LAST_INBOUND_HANDLER, SocksPortUnificationServerHandler.class.getName(),
                            new SocksPortUnificationServerHandler());
            agentChannelPipeline.addBefore(IAgentConst.LAST_INBOUND_HANDLER, SocksAgentEntryHandler.class.getName(),
                    socksAgentEntryHandler);
            agentChannelPipeline.addBefore(IAgentConst.LAST_INBOUND_HANDLER, IdleStateHandler.class.getName(),
                    new IdleStateHandler(0,
                            0,
                            this.agentConfiguration.getAgentChannelAllIdleSeconds()));
            agentChannelPipeline
                    .addBefore(IAgentConst.LAST_INBOUND_HANDLER,
                            SocksAgentCleanupInactiveAgentChannelHandler.class.getName(),
                            this.socksAgentCleanupInactiveAgentChannelHandler);
            agentChannelPipeline.remove(this);
            agentChannelContext.fireChannelRead(messageBuf);
            return;
        }
        PpaassLogger.INSTANCE
                .debug(
                        () -> "Incoming request is a http request, agent channel = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText()});
        agentChannel.attr(IAgentConst.CHANNEL_PROTOCOL_CATEGORY)
                .setIfAbsent(ChannelProtocolCategory.HTTP);
        agentChannelPipeline.addLast(HttpServerCodec.class.getName(), new HttpServerCodec());
        agentChannelPipeline.addLast(HttpObjectAggregator.class.getName(),
                new HttpObjectAggregator(Integer.MAX_VALUE, true));
//        agentChannelPipeline.addLast(httpAgentProtocolHandler);
        agentChannelPipeline.remove(this);
        agentChannelContext.fireChannelRead(messageBuf);
    }
}
