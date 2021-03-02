package com.ppaass.agent.handler.http;

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
class HttpAgentP2AHandler extends ChannelInboundHandlerAdapter {
    static {
        PpaassLogger.INSTANCE.register(HttpAgentP2AHandler.class);
    }

    @Override
    public void channelActive(ChannelHandlerContext proxyChannelContext) throws Exception {
        super.channelActive(proxyChannelContext);
        proxyChannelContext.read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext proxyChannelContext) throws Exception {
        super.channelReadComplete(proxyChannelContext);
        var proxyChannel = proxyChannelContext.channel();
        proxyChannel.read();
        var connectionInfo = proxyChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo != null) {
            var agentChannel = connectionInfo.getAgentChannel();
            if (agentChannel.isWritable()) {
                proxyChannel.read();
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext proxyChannelContext, Object msg) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var connectionInfo = proxyChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            PpaassLogger.INSTANCE.error(HttpAgentP2AHandler.class,
                    () -> "Close proxy channel because of connection info not exist, proxy channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText()});
            proxyChannel.close();
            return;
        }
        var agentChannel = connectionInfo.getAgentChannel();
        agentChannel.writeAndFlush(msg).addListener((ChannelFutureListener) agentChannelFuture -> {
            if (agentChannelFuture.isSuccess()) {
                proxyChannel.read();
                return;
            }
            PpaassLogger.INSTANCE.trace(HttpAgentP2AHandler.class,
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
            proxyChannel.close().addListener(future -> {
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            });
        });
    }
}
