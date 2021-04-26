package com.ppaass.agent.business;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class ClearClosedAgentChannelHandler extends ChannelInboundHandlerAdapter {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final AgentConfiguration agentConfiguration;

    public ClearClosedAgentChannelHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void channelRead(ChannelHandlerContext proxyChannelContext, Object msg) throws Exception {
        this.cleanupClosedAgentChannels(proxyChannelContext);
        super.channelRead(proxyChannelContext, msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext proxyChannelContext) throws Exception {
        this.cleanupClosedAgentChannels(proxyChannelContext);
        super.channelInactive(proxyChannelContext);
    }

    private void cleanupClosedAgentChannels(ChannelHandlerContext proxyChannelContext) {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannelWrappers = proxyChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
        if (agentChannelWrappers == null) {
            return;
        }
        logger.debug(() -> "Begin to cleanup closed agent channels");
        agentChannelWrappers.forEach((agentChannelId, agentChannelWrapper) -> {
            if (!agentChannelWrapper.isClosed()) {
                return;
            }
            var closeTime = agentChannelWrapper.getCloseTime();
            if (System.currentTimeMillis() - closeTime >= this.agentConfiguration.getDelayCloseTimeMillis()) {
                agentChannelWrappers.remove(agentChannelId);
                logger
                        .debug(() -> "Cleanup closed agent channel, agent channel = {}, proxy channel = {}",
                                () -> new Object[]{agentChannelId, proxyChannel.id().asLongText()});
            }
        });
    }
}
