package com.ppaass.common.handler;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

@ChannelHandler.Sharable
public class ChannelCleanupHandler extends ChannelInboundHandlerAdapter {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
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
        channel.close();
    }
}
