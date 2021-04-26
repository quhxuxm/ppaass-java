package com.ppaass.agent.business.sa;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;

import java.util.List;

class SAUdpProtocolMessageDecoder extends MessageToMessageDecoder<DatagramPacket> {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();

    @Override
    protected void decode(ChannelHandlerContext agentUdpChannelContext, DatagramPacket udpMessage, List<Object> out)
            throws Exception {
        var udpMessageContent = udpMessage.content();
        var rsv = udpMessageContent.readUnsignedShort();
        var frag = udpMessageContent.readByte();
        var addrType = Socks5AddressType.valueOf(udpMessageContent.readByte());
        var targetAddress = Socks5AddressDecoder.DEFAULT.decodeAddress(addrType, udpMessageContent);
        var targetPort = udpMessageContent.readShort();
        var data = new byte[udpMessageContent.readableBytes()];
        udpMessageContent.readBytes(data);
        var socks5UdpMessage = new SAUdpProtocolMessage(
                udpMessage.sender(),
                udpMessage.recipient(),
                rsv,
                frag,
                addrType,
                targetAddress,
                targetPort,
                data
        );
        logger.debug(SAUdpProtocolMessageDecoder.class,
                () -> "Decode socks5 udp message, agent channel = {}, proxy channel = {}, udp message:\n{}\n",
                () -> new Object[]{
                        agentUdpChannelContext.channel().id().asLongText(),
                        ByteBufUtil.prettyHexDump(udpMessageContent)
                });
        out.add(socks5UdpMessage);
    }
}
