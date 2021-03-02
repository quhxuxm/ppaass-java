package com.ppaass.agent.handler.socks;

import com.ppaass.agent.handler.socks.bo.SocksAgentUdpRequestMessage;
import com.ppaass.common.log.PpaassLogger;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;

import java.util.List;

class SocksAgentUdpMessageDecoder extends MessageToMessageDecoder<DatagramPacket> {
    static {
        PpaassLogger.INSTANCE.register(SocksAgentUdpMessageDecoder.class);
    }

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
        var socks5UdpMessage = new SocksAgentUdpRequestMessage(
                udpMessage.sender(),
                udpMessage.recipient(),
                rsv,
                frag,
                addrType,
                targetAddress,
                targetPort,
                data
        );
        PpaassLogger.INSTANCE.debug(SocksAgentUdpMessageDecoder.class,
                () -> "Decode socks5 udp message, agent channel = {}, proxy channel = {}, udp message:\n{}\n",
                () -> new Object[]{
                        agentUdpChannelContext.channel().id().asLongText(),
                        ByteBufUtil.prettyHexDump(udpMessageContent)
                });
        out.add(socks5UdpMessage);
    }
}
