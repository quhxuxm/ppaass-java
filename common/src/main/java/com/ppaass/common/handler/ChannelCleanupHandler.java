package com.ppaass.common.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class ChannelCleanupHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(ChannelCleanupHandler.class);
    public static final ChannelCleanupHandler INSTANCE = new ChannelCleanupHandler();

    private ChannelCleanupHandler() {
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext channelContext, Object evt) throws Exception {
        if (!(evt instanceof IdleStateEvent)) {
            channelContext.fireUserEventTriggered(evt);
            return;
        }
        IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
        if (IdleState.ALL_IDLE != idleStateEvent.state()) {
            channelContext.fireUserEventTriggered(idleStateEvent);
            return;
        }
        Channel channel = channelContext.channel();
        if (channel.isActive()) {
            channel.close();
        }
        logger.debug("Cleanup idle channel: {}", channel);
    }
}
