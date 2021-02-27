package com.ppaass.agent.handler.http;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class HttpAgentP2AHandler extends ChannelInboundHandlerAdapter {
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
            proxyChannel.close();
            return;
        }
        var agentChannel = connectionInfo.getAgentChannel();
        agentChannel.writeAndFlush(msg).addListener((ChannelFutureListener) agentChannelFuture -> {
            if (agentChannelFuture.isSuccess()) {
                proxyChannel.read();
                return;
            }
            proxyChannel.close();
            agentChannel.close();
        });
    }
}
