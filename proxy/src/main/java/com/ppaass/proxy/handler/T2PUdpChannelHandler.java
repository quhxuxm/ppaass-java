package com.ppaass.proxy.handler;

import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.message.MessageSerializer;
import com.ppaass.common.message.ProxyMessage;
import com.ppaass.common.message.ProxyMessageBody;
import com.ppaass.common.message.ProxyMessageBodyType;
import com.ppaass.proxy.IProxyConstant;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@ChannelHandler.Sharable
public class T2PUdpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private static final Logger logger = LoggerFactory.getLogger(T2PUdpChannelHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext targetUdpChannelContext, DatagramPacket targetUdpMessage)
            throws Exception {
        var udpConnectionInfo =
                targetUdpChannelContext.channel().attr(IProxyConstant.UDP_CONNECTION_INFO)
                        .get();
        var targetUdpMessageContent = targetUdpMessage.content();
        var sender = targetUdpMessage.sender();
        var proxyMessageBody =
                new ProxyMessageBody(
                        MessageSerializer.INSTANCE.generateUuid(),
                        udpConnectionInfo.getUserToken(),
                        sender.getHostName(),
                        sender.getPort(),
                        ProxyMessageBodyType.OK_UDP,
                        ByteBufUtil.getBytes(targetUdpMessageContent));
        var proxyMessage =
                new ProxyMessage(
                        MessageSerializer.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        proxyMessageBody);
        logger.debug(
                "Receive udp package from target: {}, data:\n{}\n\nproxy message: \n{}\n",
                targetUdpMessage,
                ByteBufUtil.prettyHexDump(targetUdpMessageContent), proxyMessage);
        udpConnectionInfo.getProxyTcpChannel().writeAndFlush(proxyMessage);
    }
}
