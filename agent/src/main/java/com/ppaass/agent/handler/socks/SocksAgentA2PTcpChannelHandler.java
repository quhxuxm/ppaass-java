package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.message.AgentMessage;
import com.ppaass.common.message.AgentMessageBody;
import com.ppaass.common.message.AgentMessageBodyType;
import com.ppaass.common.message.MessageSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SocksAgentA2PTcpChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(SocksAgentA2PTcpChannelHandler.class);
    private final AgentConfiguration agentConfiguration;

    SocksAgentA2PTcpChannelHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void channelActive(ChannelHandlerContext agentChannelContext) throws Exception {
        super.channelActive(agentChannelContext);
        var agentChannel = agentChannelContext.channel();
        agentChannel.read();
        var tcpConnectionInfo = agentChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo != null) {
            var proxyChannel = tcpConnectionInfo.getProxyTcpChannel();
            proxyChannel.read();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentChannelContext, ByteBuf originalAgentData) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var tcpConnectionInfo = agentChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            logger.error(
                    "Fail write agent original message to proxy because of no connection information attached, agent channel = {}",
                    agentChannel.id().asLongText());
            return;
        }
        var proxyTcpChannel = tcpConnectionInfo.getProxyTcpChannel();
        var originalAgentDataByteArray = new byte[originalAgentData.readableBytes()];
        originalAgentData.readBytes(originalAgentDataByteArray);
        var agentMessageBody = new AgentMessageBody(
                MessageSerializer.INSTANCE.generateUuid(),
                this.agentConfiguration.getUserToken(),
                tcpConnectionInfo.getTargetHost(),
                tcpConnectionInfo.getTargetPort(),
                AgentMessageBodyType.TCP_DATA,
                originalAgentDataByteArray);
        var agentMessage = new AgentMessage(
                MessageSerializer.INSTANCE.generateUuidInBytes(),
                EncryptionType.choose(),
                agentMessageBody);
        logger.debug(
                "Forward client original message to proxy, agent channel = {}, proxy channel = {}",
                agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText());
        proxyTcpChannel.writeAndFlush(agentMessage).addListener((ChannelFutureListener) proxyChannelFuture -> {
            if (proxyChannelFuture.isSuccess()) {
                logger.debug(
                        "Success forward client original message to proxy, agent channel = {}, proxy channel = {}",
                        agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText());
                agentChannel.read();
                proxyTcpChannel.read();
                return;
            }
            logger.error(
                    "Fail forward client original message to proxy, agent channel = {}, proxy channel = {}",
                    agentChannel.id().asLongText(), proxyTcpChannel.id().asLongText());
            agentChannel.close();
            proxyTcpChannel.close();
        });
    }
}
