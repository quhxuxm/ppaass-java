package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.common.util.UUIDUtil;
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
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final AgentConfiguration agentConfiguration;

    SASendTcpDataToProxyHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext agentChannelContext) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var proxyTcpChannel = agentChannel.attr(ISAConstant.IAgentChannelConstant.PROXY_CHANNEL).get();
        if (proxyTcpChannel == null) {
            return;
        }
        if (proxyTcpChannel.isActive()) {
            proxyTcpChannel.close();
        }
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
        logger.debug(
                () -> "Forward client original message to proxy, agent channel = {}, proxy channel = {}",
                () -> new Object[]{
                        agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText()
                });
        proxyTcpChannel.writeAndFlush(agentMessage).addListener((ChannelFutureListener) proxyChannelFuture -> {
            if (proxyChannelFuture.isSuccess()) {
                logger.debug(
                        () -> "Success forward client original message to proxy, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText()
                        });
                return;
            }
            logger.error(
                    () -> "Fail forward client original message to proxy because of exception, agent channel = {}, proxy channel = {}",
                    () -> new Object[]{
                            agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText(),
                            proxyChannelFuture.cause()
                    });
            agentChannel.close();
        });
    }
}
