package com.ppaass.agent.business.http;

import com.ppaass.common.log.PpaassLogger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class HttpAgentSendPureDataToAgentHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext proxyChannelContext, Object msg) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var connectionInfo = proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "Close proxy channel because of connection info not exist, proxy channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText()});
            var channelPool =
                    proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                            .get();
            channelPool.returnObject(proxyChannel);
            return;
        }
        var agentChannel = connectionInfo.getAgentChannel();
        agentChannel.writeAndFlush(msg).addListener((ChannelFutureListener) agentChannelFuture -> {
            if (agentChannelFuture.isSuccess()) {
                return;
            }
            PpaassLogger.INSTANCE.trace(
                    () -> "Receive proxy data, agent channel = {}, proxy channel = {}, proxy data: \n{}\n",
                    () -> {
                        var messageToPrint = msg;
                        if (messageToPrint instanceof ByteBuf) {
                            messageToPrint = ByteBufUtil.prettyHexDump((ByteBuf) msg);
                        }
                        return new Object[]{
                                agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                                messageToPrint
                        };
                    });
            var channelPool =
                    proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                            .get();
            channelPool.returnObject(proxyChannel);
            var failResponse =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
        });
    }
}
