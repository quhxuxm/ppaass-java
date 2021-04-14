package com.ppaass.proxy.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.proxy.IProxyConstant;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.stereotype.Service;

@Service
@ChannelHandler.Sharable
public class P2ATcpInactiveChannelCleanupHandler extends ChannelInboundHandlerAdapter {
    static {
        PpaassLogger.INSTANCE.register(P2ATcpInactiveChannelCleanupHandler.class);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext proxyChannelContext, Object evt) throws Exception {
        if (!(evt instanceof IdleStateEvent)) {
            proxyChannelContext.fireUserEventTriggered(evt);
            return;
        }
        IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
        if (IdleState.ALL_IDLE != idleStateEvent.state()) {
            proxyChannelContext.fireUserEventTriggered(idleStateEvent);
            return;
        }
        var proxyChannel = proxyChannelContext.channel();
        var udpConnectionInfo =
                proxyChannel.attr(IProxyConstant.UDP_CONNECTION_INFO).get();
        if (udpConnectionInfo != null) {
            return;
        }
        var tcpConnectionInfo =
                proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            return;
        }
        proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).remove();
        tcpConnectionInfo.getTargetTcpChannel().attr(IProxyConstant.TCP_CONNECTION_INFO).remove();
        tcpConnectionInfo.getTargetTcpChannel().close();
        tcpConnectionInfo.getProxyTcpChannel().close();
    }
}
