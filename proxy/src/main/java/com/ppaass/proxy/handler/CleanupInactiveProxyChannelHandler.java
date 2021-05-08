package com.ppaass.proxy.handler;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.stereotype.Service;

@Service
@ChannelHandler.Sharable
public class CleanupInactiveProxyChannelHandler extends ChannelInboundHandlerAdapter {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();

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
        if (proxyChannel.isActive()) {
            proxyChannel.close();
            logger.info(() -> "Close proxy channel as it is idle for a long time, proxy channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText()});
        }
    }
}
