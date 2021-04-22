package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.message.AgentMessage;
import com.ppaass.protocol.vpn.message.AgentMessageBody;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import com.ppaass.protocol.vpn.message.EncryptionType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SASendTcpDataToProxyHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final AgentConfiguration agentConfiguration;

    SASendTcpDataToProxyHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void channelInactive(ChannelHandlerContext agentChannelContext) throws Exception {
        var agentChannel = agentChannelContext.channel();
        PpaassLogger.INSTANCE
                .debug(() -> "Begin to unregister agent channel, agent channel = {}",
                        () -> new Object[]{agentChannel.id().asLongText()});
        var proxyTcpChannel =
                agentChannel.attr(ISAConstant.IAgentChannelConstant.PROXY_CHANNEL).get();
        var socksProxyTcpChannelPool =
                proxyTcpChannel.attr(ISAConstant.IProxyChannelConstant.CHANNEL_POOL).get();
        try {
            socksProxyTcpChannelPool.returnObject(proxyTcpChannel);
        } catch (Exception e) {
            PpaassLogger.INSTANCE
                    .debug(() -> "Fail to return proxy channel to pool because of exception, proxy channel = {}",
                            () -> new Object[]{
                                    proxyTcpChannel.id().asLongText(), e
                            });
        }
        PpaassLogger.INSTANCE
                .debug(() -> "Agent channel success unregistered, agent channel = {}",
                        () -> new Object[]{agentChannel.id().asLongText()});
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext agentChannelContext) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var proxyTcpChannel =
                agentChannel.attr(ISAConstant.IAgentChannelConstant.PROXY_CHANNEL).get();
        agentChannel.attr(ISAConstant.IAgentChannelConstant.PROXY_CHANNEL).set(null);
        if (proxyTcpChannel == null) {
            PpaassLogger.INSTANCE
                    .debug(() -> "No proxy channel attached to agent channel, skip the step to unregister itself from proxy channel, agent channel = {}",
                            () -> new Object[]{agentChannel.id().asLongText()});
            return;
        }
        var agentChannelsOnProxyChannel = proxyTcpChannel.attr(ISAConstant.IProxyChannelConstant.AGENT_CHANNELS).get();
        agentChannelsOnProxyChannel.remove(agentChannel.id().asLongText());
        agentChannel.attr(IAgentConst.CHANNEL_PROTOCOL_CATEGORY).set(null);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentChannelContext, ByteBuf originalAgentData) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var proxyTcpChannel = agentChannel.attr(ISAConstant.IAgentChannelConstant.PROXY_CHANNEL).get();
        var originalAgentDataByteArray = new byte[originalAgentData.readableBytes()];
        originalAgentData.readBytes(originalAgentDataByteArray);
        var targetHost = agentChannel.attr(ISAConstant.IAgentChannelConstant.TARGET_HOST).get();
        var targetPort = agentChannel.attr(ISAConstant.IAgentChannelConstant.TARGET_PORT).get();
        var agentMessageBody = new AgentMessageBody(
                UUIDUtil.INSTANCE.generateUuid(),
                this.agentConfiguration.getAgentInstanceId(),
                this.agentConfiguration.getUserToken(),
                this.agentConfiguration.getAgentSourceAddress(),
                this.agentConfiguration.getTcpPort(),
                targetHost,
                targetPort,
                AgentMessageBodyType.TCP_DATA,
                agentChannel.id().asLongText(),
                null,
                originalAgentDataByteArray);
        var agentMessage = new AgentMessage(
                UUIDUtil.INSTANCE.generateUuidInBytes(),
                EncryptionType.choose(),
                agentMessageBody);
        PpaassLogger.INSTANCE.debug(
                () -> "Forward client original message to proxy, agent channel = {}, proxy channel = {}",
                () -> new Object[]{
                        agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText()
                });
        proxyTcpChannel.writeAndFlush(agentMessage).addListener((ChannelFutureListener) proxyChannelFuture -> {
            if (proxyChannelFuture.isSuccess()) {
                PpaassLogger.INSTANCE.debug(
                        () -> "Success forward client original message to proxy, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText()
                        });
                return;
            }
            PpaassLogger.INSTANCE.error(
                    () -> "Fail forward client original message to proxy because of exception, agent channel = {}, proxy channel = {}",
                    () -> new Object[]{
                            agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText(),
                            proxyChannelFuture.cause()
                    });
            agentChannel.close();
        });
    }
}
