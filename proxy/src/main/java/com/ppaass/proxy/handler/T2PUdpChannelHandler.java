package com.ppaass.proxy.handler;

import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.*;
import com.ppaass.proxy.IProxyConstant;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.springframework.stereotype.Service;

@Service
@ChannelHandler.Sharable
public class T2PUdpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    static {
        PpaassLogger.INSTANCE.register(T2PUdpChannelHandler.class);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext targetUdpChannelContext, DatagramPacket targetUdpMessage)
            throws Exception {
        var udpConnectionInfo =
                targetUdpChannelContext.channel().attr(IProxyConstant.UDP_CONNECTION_INFO)
                        .get();
        var targetUdpMessageContent = targetUdpMessage.content();
        var sender = targetUdpMessage.sender();
        var udpData = ByteBufUtil.getBytes(targetUdpMessageContent);
        var udpMessageContent = new UdpTransferMessageContent();
        udpMessageContent.setData(udpData);
        udpMessageContent.setOriginalAddrType(udpConnectionInfo.getAddrType());
        udpMessageContent.setOriginalSourceAddress(udpConnectionInfo.getSourceAddress());
        udpMessageContent.setOriginalSourcePort(udpConnectionInfo.getSourcePort());
        udpMessageContent.setOriginalDestinationAddress(udpConnectionInfo.getDestinationAddress());
        udpMessageContent.setOriginalDestinationPort(udpConnectionInfo.getDestinationPort());
        var proxyMessageBody =
                new ProxyMessageBody(
                        MessageSerializer.INSTANCE.generateUuid(),
                        udpConnectionInfo.getUserToken(),
                        sender.getHostName(),
                        sender.getPort(),
                        ProxyMessageBodyType.OK_UDP,
                        MessageSerializer.JSON_OBJECT_MAPPER.writeValueAsBytes(udpMessageContent));
        var proxyMessage =
                new ProxyMessage(
                        MessageSerializer.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        proxyMessageBody);
        PpaassLogger.INSTANCE.debug(T2PUdpChannelHandler.class,
                () -> "Receive udp package from target: {}, data:\n{}\n\nproxy message: \n{}\n",
                () -> new Object[]{
                        targetUdpMessage,
                        ByteBufUtil.prettyHexDump(targetUdpMessageContent), proxyMessage
                });
        udpConnectionInfo.getProxyTcpChannel().writeAndFlush(proxyMessage);
    }
}
