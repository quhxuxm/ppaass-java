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
public class InactiveChannelCleanupHandler extends ChannelInboundHandlerAdapter {
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
        var targetChannelsInProxyChannels = proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNELS).get();
        if (targetChannelsInProxyChannels != null) {
            targetChannelsInProxyChannels.forEach((key, channel) -> {
                PpaassLogger.INSTANCE
                        .debug(() -> "Close target channel on proxy channel cleanup, target channel key = {}",
                                () -> new Object[]{key});
                channel.close();
            });
            targetChannelsInProxyChannels.clear();
        }
        proxyChannel.close();
    }
}
