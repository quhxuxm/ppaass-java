package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.handler.socks.bo.SocksAgentUdpRequestMessage;
import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.message.AgentMessage;
import com.ppaass.common.message.AgentMessageBody;
import com.ppaass.common.message.AgentMessageBodyType;
import com.ppaass.common.message.MessageSerializer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class SocksAgentA2PUdpChannelHandler extends SimpleChannelInboundHandler<SocksAgentUdpRequestMessage> {
    private final AgentConfiguration agentConfiguration;

    SocksAgentA2PUdpChannelHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void channelActive(ChannelHandlerContext agentUdpChannelContext) throws Exception {
        super.channelActive(agentUdpChannelContext);
        agentUdpChannelContext.read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext agentUdpChannelContext) throws Exception {
        super.channelReadComplete(agentUdpChannelContext);
        agentUdpChannelContext.read();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentUdpChannelContext,
                                SocksAgentUdpRequestMessage socks5UdpMessage)
            throws Exception {
        var udpConnectionInfo =
                agentUdpChannelContext.channel().attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO).get();
        udpConnectionInfo.setClientSenderHost(socks5UdpMessage.getUdpMessageSender().getHostName());
        udpConnectionInfo.setClientSenderPort(socks5UdpMessage.getUdpMessageSender().getPort());
        udpConnectionInfo.setClientRecipientHost(socks5UdpMessage.getTargetHost());
        udpConnectionInfo.setClientRecipientPort(socks5UdpMessage.getTargetPort());
        agentUdpChannelContext.channel().attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO).set(udpConnectionInfo);
        var udpData = socks5UdpMessage.getData();
        var agentMessageBody =
                new AgentMessageBody(
                        MessageSerializer.INSTANCE.generateUuid(),
                        this.agentConfiguration.getUserToken(),
                        socks5UdpMessage.getTargetHost(),
                        socks5UdpMessage.getTargetPort(),
                        AgentMessageBodyType.UDP_DATA,
                        udpData);
        var agentMessage =
                new AgentMessage(
                        MessageSerializer.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        agentMessageBody);
        var proxyTcpChannelForUdpTransfer = udpConnectionInfo.getProxyTcpChannel();
        proxyTcpChannelForUdpTransfer.writeAndFlush(agentMessage)
                .addListener((ChannelFutureListener) proxyChannelFuture -> {
                    agentUdpChannelContext.channel().read();
                    proxyTcpChannelForUdpTransfer.read();
                });
    }
}
