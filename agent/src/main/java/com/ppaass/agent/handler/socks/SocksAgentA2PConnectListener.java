package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.handler.socks.bo.SocksAgentTcpConnectionInfo;
import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.message.AgentMessage;
import com.ppaass.common.message.AgentMessageBody;
import com.ppaass.common.message.AgentMessageBodyType;
import com.ppaass.common.message.MessageSerializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocksAgentA2PConnectListener implements ChannelFutureListener {
    private static final Logger logger = LoggerFactory.getLogger(SocksAgentA2PConnectListener.class);
    private final Channel agentChannel;
    private final Socks5CommandRequest socks5CommandRequest;
    private final AgentConfiguration agentConfiguration;

    public SocksAgentA2PConnectListener(Channel agentChannel,
                                        Socks5CommandRequest socks5CommandRequest,
                                        AgentConfiguration agentConfiguration) {
        this.agentChannel = agentChannel;
        this.socks5CommandRequest = socks5CommandRequest;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void operationComplete(ChannelFuture proxyChannelFuture) throws Exception {
        if (!proxyChannelFuture.isSuccess()) {
            logger.error("Fail connect to proxy, agent channel = {}", agentChannel.id().asLongText());
            agentChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                    socks5CommandRequest.dstAddrType()))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        logger.debug("Success connect to proxy, agent channel = {}", agentChannel.id().asLongText());
        var proxyChannel = proxyChannelFuture.channel();
        var tcpConnectionInfo = new SocksAgentTcpConnectionInfo(
                socks5CommandRequest.dstAddr(),
                socks5CommandRequest.dstPort(),
                socks5CommandRequest.dstAddrType(),
                agentConfiguration.getUserToken(),
                true,
                agentChannel,
                proxyChannel);
        proxyChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO)
                .setIfAbsent(tcpConnectionInfo);
        agentChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO)
                .setIfAbsent(tcpConnectionInfo);
        var agentMessageBody = new AgentMessageBody(
                MessageSerializer.INSTANCE.generateUuid(),
                agentConfiguration.getUserToken(),
                tcpConnectionInfo.getTargetHost(),
                tcpConnectionInfo.getTargetPort(),
                AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE,
                new byte[]{});
        var agentMessage = new AgentMessage(
                MessageSerializer.INSTANCE.generateUuidInBytes(),
                EncryptionType.choose(),
                agentMessageBody);
        var agentChannelPipeline = this.agentChannel.pipeline();
        try {
            agentChannelPipeline.remove(SocksAgentProtocolHandler.class.getName());
        } catch (Exception e) {
            logger.debug(
                    "Fail to remove SocksV5Handler from proxy channel pipeline, proxy channel = {}",
                    proxyChannel.id().asLongText());
        }
        logger.debug(
                "Send CONNECT_WITH_KEEP_ALIVE from agent to proxy [BEGIN] , agent channel = {}, proxy channel = {}",
                agentChannel.id().asLongText(), proxyChannel.id().asLongText());
        proxyChannel.writeAndFlush(agentMessage)
                .addListener((ChannelFutureListener) proxyWriteChannelFuture -> {
                    if (proxyWriteChannelFuture.isSuccess()) {
                        logger.debug(
                                "Send CONNECT_WITH_KEEP_ALIVE from agent to proxy [SUCCESS], agent channel = {}, proxy channel = {}",
                                agentChannel.id().asLongText(), proxyChannel.id().asLongText());
                        proxyChannel.read();
                        return;
                    }
                    logger.debug(
                            "Send CONNECT_WITH_KEEP_ALIVE from agent to proxy [FAIL], agent channel = {}, proxy channel = {}",
                            agentChannel.id().asLongText(), proxyChannel.id().asLongText());
                    agentChannel.writeAndFlush(
                            new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                                    tcpConnectionInfo.getTargetAddressType()))
                            .addListener(ChannelFutureListener.CLOSE);
                });
    }
}
