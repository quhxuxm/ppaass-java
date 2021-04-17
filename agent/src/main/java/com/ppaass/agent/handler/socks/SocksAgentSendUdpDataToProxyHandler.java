package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.handler.socks.bo.SocksAgentUdpProtocolMessage;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.common.util.UUIDUtil;
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
class SocksAgentSendUdpDataToProxyHandler extends SimpleChannelInboundHandler<SocksAgentUdpProtocolMessage> {
    private final AgentConfiguration agentConfiguration;

    SocksAgentSendUdpDataToProxyHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentUdpChannelContext,
                                SocksAgentUdpProtocolMessage socks5UdpMessage)
            throws Exception {
        var agentChannel = agentUdpChannelContext.channel();
        var udpConnectionInfo =
                agentUdpChannelContext.channel().attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO).get();
        udpConnectionInfo.setClientSenderHost(socks5UdpMessage.getUdpMessageSender().getHostName());
        udpConnectionInfo.setClientSenderPort(socks5UdpMessage.getUdpMessageSender().getPort());
        udpConnectionInfo.setClientRecipientHost(socks5UdpMessage.getTargetHost());
        udpConnectionInfo.setClientRecipientPort(socks5UdpMessage.getTargetPort());
        var data = socks5UdpMessage.getData();
        var agentMessageBody =
                new AgentMessageBody(
                        UUIDUtil.INSTANCE.generateUuid(),
                        this.agentConfiguration.getAgentInstanceId(),
                        this.agentConfiguration.getUserToken(),
                        udpConnectionInfo.getClientSenderHost(),
                        udpConnectionInfo.getClientSenderPort(),
                        udpConnectionInfo.getClientRecipientHost(),
                        udpConnectionInfo.getClientRecipientPort(),
                        AgentMessageBodyType.UDP_DATA,
                        agentChannel.id().asLongText(),
                        null,
                        data);
        var agentMessage =
                new AgentMessage(
                        UUIDUtil.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        agentMessageBody);
        var proxyTcpChannelForUdpTransfer = udpConnectionInfo.getProxyTcpChannel();
        proxyTcpChannelForUdpTransfer.writeAndFlush(agentMessage)
                .addListener((ChannelFutureListener) proxyChannelFuture -> {
                    if (!proxyChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE
                                .error(() -> "Fail to write udp message to proxy because of exception, udp connection info: \n{}\n.",
                                        () -> new Object[]{
                                                udpConnectionInfo,
                                                proxyChannelFuture.cause()
                                        });
                        return;
                    }
                    PpaassLogger.INSTANCE
                            .debug(() -> "Success to write udp message to proxy, udp connection info: \n{}\n.",
                                    () -> new Object[]{
                                            udpConnectionInfo,
                                            proxyChannelFuture.cause()
                                    });
                });
    }
}
