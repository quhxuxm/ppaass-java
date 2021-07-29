package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.message.AgentMessage;
import com.ppaass.protocol.vpn.message.AgentMessageBody;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import com.ppaass.protocol.vpn.message.EncryptionType;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SASendUdpDataToProxyHandler extends SimpleChannelInboundHandler<SAUdpProtocolMessage> {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final AgentConfiguration agentConfiguration;
    private final SAProxyResourceManager saProxyResourceManager;

    SASendUdpDataToProxyHandler(AgentConfiguration agentConfiguration,
                                SAProxyResourceManager saProxyResourceManager) {
        this.agentConfiguration = agentConfiguration;
        this.saProxyResourceManager = saProxyResourceManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentUdpChannelContext,
                                SAUdpProtocolMessage socks5UdpMessage)
            throws Exception {
        var agentUdpChannel = agentUdpChannelContext.channel();
        var udpConnectionInfo =
                agentUdpChannel.attr(ISAConstant.SOCKS_UDP_CONNECTION_INFO).get();
        udpConnectionInfo.setClientSenderHost(socks5UdpMessage.getUdpMessageSender().getHostName());
        udpConnectionInfo.setClientSenderPort(socks5UdpMessage.getUdpMessageSender().getPort());
        udpConnectionInfo.setClientRecipientHost(socks5UdpMessage.getTargetHost());
        udpConnectionInfo.setClientRecipientPort(socks5UdpMessage.getTargetPort());
        agentUdpChannel.attr(ISAConstant.SOCKS_UDP_CONNECTION_INFO).set(udpConnectionInfo);
        udpConnectionInfo.getAgentTcpChannel().attr(ISAConstant.SOCKS_UDP_CONNECTION_INFO)
                .set(udpConnectionInfo);
        udpConnectionInfo.getProxyTcpChannel().attr(ISAConstant.SOCKS_UDP_CONNECTION_INFO)
                .set(udpConnectionInfo);
        var data = socks5UdpMessage.getData();
        var agentMessageBody =
                new AgentMessageBody(
                        UUIDUtil.INSTANCE.generateUuid(),
                        this.agentConfiguration.getAgentInstanceId(),
                        this.agentConfiguration.getUserToken(),
                        udpConnectionInfo.getClientSenderHost(),
                        udpConnectionInfo.getClientSenderPort(),
                        socks5UdpMessage.getTargetHost(),
                        socks5UdpMessage.getTargetPort(),
                        AgentMessageBodyType.UDP_DATA,
                        agentUdpChannel.id().asLongText(),
                        null,
                        data);
        var agentMessage =
                new AgentMessage(
                        UUIDUtil.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        agentMessageBody);
        var proxyTcpChannelForUdpTransfer = udpConnectionInfo.getProxyTcpChannel();
        if (!proxyTcpChannelForUdpTransfer.isActive()) {
            proxyTcpChannelForUdpTransfer =
                    this.saProxyResourceManager.getProxyTcpChannelBootstrap().connect().sync().channel();
            proxyTcpChannelForUdpTransfer.attr(ISAConstant.SOCKS_UDP_CONNECTION_INFO)
                    .set(udpConnectionInfo);
            proxyTcpChannelForUdpTransfer.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNEL).set(agentUdpChannel);
        }
        proxyTcpChannelForUdpTransfer.writeAndFlush(agentMessage)
                .addListener((ChannelFutureListener) proxyChannelFuture -> {
                    if (!proxyChannelFuture.isSuccess()) {
                        logger
                                .error(() -> "Fail to write udp message to proxy because of exception, udp connection info: \n{}\n.",
                                        () -> new Object[]{
                                                udpConnectionInfo,
                                                proxyChannelFuture.cause()
                                        });
                        return;
                    }
                    logger
                            .debug(() -> "Success to write udp message to proxy, udp connection info: \n{}\n.",
                                    () -> new Object[]{
                                            udpConnectionInfo,
                                            proxyChannelFuture.cause()
                                    });
                });
    }
}
