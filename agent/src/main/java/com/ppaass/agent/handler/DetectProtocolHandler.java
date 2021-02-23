package com.ppaass.agent.handler;

import com.ppaass.agent.ChannelProtocolCategory;
import com.ppaass.agent.IAgentConst;
import com.ppaass.agent.handler.http.HttpProtocolHandler;
import com.ppaass.agent.handler.socks.SocksAgentProtocolHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class DetectProtocolHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DetectProtocolHandler.class);
    private final SocksAgentProtocolHandler socksAgentProtocolHandler;
    private final HttpProtocolHandler httpProtocolHandler;

    public DetectProtocolHandler(SocksAgentProtocolHandler socksAgentProtocolHandler,
                                 HttpProtocolHandler httpProtocolHandler) {
        this.socksAgentProtocolHandler = socksAgentProtocolHandler;
        this.httpProtocolHandler = httpProtocolHandler;
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
            logger.debug("Incoming request protocol is: {}", channelProtocolType);
            agentChannelContext.fireChannelRead(msg);
            return;
        }
        var messageBuf = (ByteBuf) msg;
        var readerIndex = messageBuf.readerIndex();
        if (messageBuf.writerIndex() == readerIndex) {
            logger.debug("Incoming request reader index is the same as writer index.");
            agentChannel.close();
            return;
        }
        var protocolVersionByte = messageBuf.getByte(readerIndex);
        var agentChannelPipeline = agentChannelContext.pipeline();
        if (SocksVersion.SOCKS4a.byteValue() == protocolVersionByte ||
                SocksVersion.SOCKS5.byteValue() == protocolVersionByte) {
            logger.debug("Incoming request is a socks request.");
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
        logger.debug("Incoming request is a http request.");
        agentChannel.attr(IAgentConst.CHANNEL_PROTOCOL_CATEGORY)
                .setIfAbsent(ChannelProtocolCategory.HTTP);
        agentChannelPipeline.addLast(HttpServerCodec.class.getName(), new HttpServerCodec());
        agentChannelPipeline.addLast(HttpObjectAggregator.class.getName(),
                new HttpObjectAggregator(Integer.MAX_VALUE, true));
        agentChannelPipeline.addLast(httpProtocolHandler);
        agentChannelPipeline.remove(this);
        agentChannelContext.fireChannelRead(messageBuf);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext agentChannelContext) throws Exception {
        super.channelReadComplete(agentChannelContext);
        agentChannelContext.read();
    }
}
