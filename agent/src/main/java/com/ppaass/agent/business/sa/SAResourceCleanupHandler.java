package com.ppaass.agent.business.sa;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.stereotype.Service;

@Service
@ChannelHandler.Sharable
public class SAResourceCleanupHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext agentChannelContext, Object evt) throws Exception {
        if (!(evt instanceof IdleStateEvent)) {
            agentChannelContext.fireUserEventTriggered(evt);
            return;
        }
        IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
        if (IdleState.ALL_IDLE != idleStateEvent.state()) {
            agentChannelContext.fireUserEventTriggered(idleStateEvent);
            return;
        }
        var agentChannel = agentChannelContext.channel();
        agentChannel.attr(ISAConstant.IAgentChannelConstant.TCP_CONNECTION_INFO).set(null);
        agentChannel.close();
    }
}
