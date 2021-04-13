package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.handler.socks.bo.SocksAgentUdpRequestMessage;
import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.message.*;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
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
        var udpMessageContent = new UdpTransferMessageContent();
        udpMessageContent.setData(socks5UdpMessage.getData());
        udpMessageContent.setOriginalSourceAddress(socks5UdpMessage.getUdpMessageSender().getAddress().getHostAddress());
        udpMessageContent.setOriginalSourcePort(socks5UdpMessage.getUdpMessageSender().getPort());
        udpMessageContent.setOriginalDestinationAddress(socks5UdpMessage.getTargetHost());
        udpMessageContent.setOriginalDestinationPort(socks5UdpMessage.getTargetPort());
        if (socks5UdpMessage.getAddressType() == Socks5AddressType.DOMAIN) {
            udpMessageContent.setOriginalAddrType(UdpTransferMessageContent.AddrType.DOMAIN);
        } else {
            if (socks5UdpMessage.getAddressType() == Socks5AddressType.IPv6) {
                udpMessageContent.setOriginalAddrType(UdpTransferMessageContent.AddrType.IPV6);
            } else {
                udpMessageContent.setOriginalAddrType(UdpTransferMessageContent.AddrType.IPV4);
            }
        }
        var agentMessageBody =
                new AgentMessageBody(
                        MessageSerializer.INSTANCE.generateUuid(),
                        this.agentConfiguration.getUserToken(),
                        udpMessageContent.getOriginalDestinationAddress(),
                        udpMessageContent.getOriginalDestinationPort(),
                        AgentMessageBodyType.UDP_DATA,
                        MessageSerializer.JSON_OBJECT_MAPPER.writeValueAsBytes(udpMessageContent));
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
