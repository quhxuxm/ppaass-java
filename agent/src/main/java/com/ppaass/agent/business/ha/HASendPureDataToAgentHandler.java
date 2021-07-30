package com.ppaass.agent.business.ha;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class HASendPureDataToAgentHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(HASendPureDataToAgentHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext proxyChannelContext, Object msg) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannel = proxyChannel.attr(IHAConstant.IProxyChannelConstant.AGENT_CHANNEL_TO_SEND_PURE_DATA).get();
        proxyChannel.attr(IHAConstant.IProxyChannelConstant.AGENT_CHANNEL_TO_SEND_PURE_DATA).set(null);
        agentChannel.writeAndFlush(msg).addListener((ChannelFutureListener) agentChannelFuture -> {
            if (agentChannelFuture.isSuccess()) {
                return;
            }
            var messageToPrint = msg;
            if (messageToPrint instanceof ByteBuf) {
                messageToPrint = ByteBufUtil.prettyHexDump((ByteBuf) msg);
            }
            logger.trace(
                    "Receive proxy data, agent channel = {}, proxy channel = {}, proxy data: \n{}\n",
                    agentChannel.id().asLongText(), proxyChannel.id().asLongText(), messageToPrint);
            var failResponse =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
        });
    }
}
