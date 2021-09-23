package com.ppaass.proxy.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@ChannelHandler.Sharable
public class CleanupInactiveProxyChannelHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(CleanupInactiveProxyChannelHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext proxyChannelContext, Object evt) throws Exception {
        if (!(evt instanceof IdleStateEvent idleStateEvent)) {
            proxyChannelContext.fireUserEventTriggered(evt);
            return;
        }
        if (IdleState.ALL_IDLE != idleStateEvent.state()) {
            proxyChannelContext.fireUserEventTriggered(idleStateEvent);
            return;
        }
        var proxyChannel = proxyChannelContext.channel();
        if (proxyChannel.isActive()) {
            proxyChannel.close();
            logger.info("Close proxy channel as it is idle for a long time, proxy channel = {}",
                    proxyChannel.id().asLongText());
        }
    }
}
