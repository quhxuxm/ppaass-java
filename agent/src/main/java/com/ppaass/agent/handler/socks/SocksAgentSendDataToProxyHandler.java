package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
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
class SocksAgentSendDataToProxyHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final AgentConfiguration agentConfiguration;

    SocksAgentSendDataToProxyHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void channelInactive(ChannelHandlerContext agentChannelContext) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var tcpConnectionInfo = agentChannel.attr(ISocksAgentConst.IAgentChannelAttr.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            return;
        }
        var proxyTcpChannel = tcpConnectionInfo.getProxyTcpChannel();
        var socksProxyTcpChannelPool = proxyTcpChannel.attr(ISocksAgentConst.IProxyChannelAttr.CHANNEL_POOL).get();
        socksProxyTcpChannelPool.returnObject(proxyTcpChannel);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentChannelContext, ByteBuf originalAgentData) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var tcpConnectionInfo = agentChannel.attr(ISocksAgentConst.IAgentChannelAttr.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "Fail write agent original message to proxy because of no connection information attached, agent channel = {}",
                    () -> new Object[]{agentChannel.id().asLongText()});
            return;
        }
        var proxyTcpChannel = tcpConnectionInfo.getProxyTcpChannel();
        var originalAgentDataByteArray = new byte[originalAgentData.readableBytes()];
        originalAgentData.readBytes(originalAgentDataByteArray);
        var agentMessageBody = new AgentMessageBody(
                UUIDUtil.INSTANCE.generateUuid(),
                this.agentConfiguration.getAgentInstanceId(),
                this.agentConfiguration.getUserToken(),
                this.agentConfiguration.getAgentSourceAddress(),
                this.agentConfiguration.getTcpPort(),
                tcpConnectionInfo.getTargetHost(),
                tcpConnectionInfo.getTargetPort(),
                AgentMessageBodyType.TCP_DATA,
                agentChannel.id().asLongText(),
                tcpConnectionInfo.getTargetChannelId(),
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
