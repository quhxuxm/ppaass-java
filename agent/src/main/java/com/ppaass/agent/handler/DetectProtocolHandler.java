package com.ppaass.agent.handler;

import com.ppaass.agent.ChannelProtocolCategory;
import com.ppaass.agent.IAgentConst;
import com.ppaass.agent.handler.http.HttpAgentProtocolHandler;
import com.ppaass.agent.handler.socks.SocksAgentProtocolHandler;
import com.ppaass.common.log.PpaassLogger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class DetectProtocolHandler extends ChannelInboundHandlerAdapter {
    static {
        PpaassLogger.INSTANCE.register(DetectProtocolHandler.class);
    }

    private final SocksAgentProtocolHandler socksAgentProtocolHandler;
    private final HttpAgentProtocolHandler httpAgentProtocolHandler;

    public DetectProtocolHandler(SocksAgentProtocolHandler socksAgentProtocolHandler,
                                 HttpAgentProtocolHandler httpAgentProtocolHandler) {
        this.socksAgentProtocolHandler = socksAgentProtocolHandler;
        this.httpAgentProtocolHandler = httpAgentProtocolHandler;
    }

    @Override
    public void channelActive(ChannelHandlerContext agentChannelContext) throws Exception {
        super.channelActive(agentChannelContext);
        agentChannelContext.read();
    }

    @Override
    public void channelRead(ChannelHandlerContext agentChannelContext, Object msg) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var channelProtocolType = agentChannel.attr(IAgentConst.CHANNEL_PROTOCOL_CATEGORY).get();
        if (channelProtocolType != null) {
            PpaassLogger.INSTANCE
                    .debug(DetectProtocolHandler.class, () -> "Incoming request protocol is: {}, agent channel = {}",
                            () -> new Object[]{channelProtocolType,
                                    agentChannel.id().asLongText()});
            agentChannelContext.fireChannelRead(msg);
            return;
        }
        var messageBuf = (ByteBuf) msg;
        var readerIndex = messageBuf.readerIndex();
        if (messageBuf.writerIndex() == readerIndex) {
            PpaassLogger.INSTANCE
                    .debug(DetectProtocolHandler.class,
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
                    .debug(DetectProtocolHandler.class,
                            () -> "Incoming request is a socks request, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()});
            agentChannel.attr(IAgentConst.CHANNEL_PROTOCOL_CATEGORY)
                    .setIfAbsent(ChannelProtocolCategory.SOCKS);
            agentChannelPipeline
                    .addBefore(IAgentConst.LAST_INBOUND_HANDLER, SocksPortUnificationServerHandler.class.getName(),
                            new SocksPortUnificationServerHandler());
            agentChannelPipeline.addBefore(IAgentConst.LAST_INBOUND_HANDLER, SocksAgentProtocolHandler.class.getName(),
                    socksAgentProtocolHandler);
            agentChannelPipeline.remove(this);
            agentChannelContext.fireChannelRead(messageBuf);
            return;
        }
        PpaassLogger.INSTANCE
                .debug(DetectProtocolHandler.class,
                        () -> "Incoming request is a http request, agent channel = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText()});
        agentChannel.attr(IAgentConst.CHANNEL_PROTOCOL_CATEGORY)
                .setIfAbsent(ChannelProtocolCategory.HTTP);
        agentChannelPipeline.addLast(HttpServerCodec.class.getName(), new HttpServerCodec());
        agentChannelPipeline.addLast(HttpObjectAggregator.class.getName(),
                new HttpObjectAggregator(Integer.MAX_VALUE, true));
        agentChannelPipeline.addLast(httpAgentProtocolHandler);
        agentChannelPipeline.remove(this);
        agentChannelContext.fireChannelRead(messageBuf);
    }
}
