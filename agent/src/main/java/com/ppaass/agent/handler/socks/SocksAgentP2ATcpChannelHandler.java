package com.ppaass.agent.handler.socks;

import com.ppaass.agent.IAgentConst;
import com.ppaass.common.message.HeartbeatInfo;
import com.ppaass.common.message.MessageSerializer;
import com.ppaass.common.message.ProxyMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
@Service
class SocksAgentP2ATcpChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    private static final Logger logger = LoggerFactory.getLogger(SocksAgentP2ATcpChannelHandler.class);

    private static class WriteToAgentChannelResultFutureListener implements ChannelFutureListener {
        private final Channel agentChannel;
        private final Channel proxyChannel;

        public WriteToAgentChannelResultFutureListener(Channel agentChannel, Channel proxyChannel) {
            this.agentChannel = agentChannel;
            this.proxyChannel = proxyChannel;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                return;
            }
            this.agentChannel.close();
            this.proxyChannel.close();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext proxyChannelContext) throws Exception {
        super.channelActive(proxyChannelContext);
        proxyChannelContext.read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext proxyChannelContext) throws Exception {
        super.channelReadComplete(proxyChannelContext);
        proxyChannelContext.read();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var tcpConnectionInfo = proxyChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO).get();
        switch (proxyMessage.getBody().getBodyType()) {
            case CONNECT_SUCCESS -> {
                if (tcpConnectionInfo == null) {
                    proxyChannel.close();
                    return;
                }
                var agentTcpChannel = tcpConnectionInfo.getAgentTcpChannel();
                agentTcpChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                        tcpConnectionInfo.getTargetAddressType(), tcpConnectionInfo.getTargetHost(),
                        tcpConnectionInfo.getTargetPort()))
                        .addListener(new WriteToAgentChannelResultFutureListener(agentTcpChannel, proxyChannel));
            }
            case HEARTBEAT -> {
                if (tcpConnectionInfo == null) {
                    proxyChannel.close();
                    return;
                }
                var agentTcpChannel = tcpConnectionInfo.getAgentTcpChannel();
                var originalData = proxyMessage.getBody().getData();
                var heartbeat = MessageSerializer.JSON_OBJECT_MAPPER.readValue(originalData, HeartbeatInfo.class);
                logger.trace(
                        "[HEARTBEAT FROM PROXY]: proxy channel = {}, agent channel = {}, heartbeat id = {}, heartbeat time = {}",
                        proxyChannel.id().asLongText()
                        , agentTcpChannel.id().asLongText(), heartbeat.getId(), heartbeat.getUtcDateTime());
                agentTcpChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                        .addListener(new WriteToAgentChannelResultFutureListener(agentTcpChannel, proxyChannel));
            }
            case CONNECT_FAIL -> {
                if (tcpConnectionInfo == null) {
                    proxyChannel.close();
                    return;
                }
                var agentTcpChannel = tcpConnectionInfo.getAgentTcpChannel();
                agentTcpChannel.writeAndFlush(
                        new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                                tcpConnectionInfo.getTargetAddressType()))
                        .addListener(future -> {
                            agentTcpChannel.close();
                            proxyChannel.close();
                        });
            }
            case OK_TCP -> {
                if (tcpConnectionInfo == null) {
                    proxyChannel.close();
                    return;
                }
                var agentTcpChannel = tcpConnectionInfo.getAgentTcpChannel();
                agentTcpChannel.writeAndFlush(
                        Unpooled.wrappedBuffer(proxyMessage.getBody().getData()))
                        .addListener(new WriteToAgentChannelResultFutureListener(agentTcpChannel, proxyChannel));
            }
            case OK_UDP -> {
                var udpConnectionInfo = proxyChannel.attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO).get();
                var recipient = new InetSocketAddress(udpConnectionInfo.getClientSenderHost(),
                        udpConnectionInfo.getClientSenderPort());
                var sender = new InetSocketAddress(IAgentConst.LOCAL_IP_ADDRESS,
                        udpConnectionInfo.getAgentUdpPort());
                var data = proxyMessage.getBody().getData();
                var socks5UdpResponseBuf = Unpooled.buffer();
                socks5UdpResponseBuf.writeByte(0);
                socks5UdpResponseBuf.writeByte(0);
                socks5UdpResponseBuf.writeByte(0);
                var clientRecipientHost = udpConnectionInfo.getClientRecipientHost();
                if (NetUtil.isValidIpV4Address(clientRecipientHost)) {
                    socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv4.byteValue());
                    Socks5AddressEncoder.DEFAULT
                            .encodeAddress(Socks5AddressType.IPv4, clientRecipientHost,
                                    socks5UdpResponseBuf);
                } else {
                    if (NetUtil.isValidIpV6Address(clientRecipientHost)) {
                        socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv6.byteValue());
                        Socks5AddressEncoder.DEFAULT
                                .encodeAddress(Socks5AddressType.IPv6, clientRecipientHost,
                                        socks5UdpResponseBuf);
                    } else {
                        socks5UdpResponseBuf.writeByte(clientRecipientHost.length());
                        Socks5AddressEncoder.DEFAULT
                                .encodeAddress(Socks5AddressType.DOMAIN,
                                        clientRecipientHost,
                                        socks5UdpResponseBuf);
                    }
                }
                socks5UdpResponseBuf.writeShort(udpConnectionInfo.getClientRecipientPort());
                socks5UdpResponseBuf.writeBytes(data);
                var udpPackage = new DatagramPacket(socks5UdpResponseBuf, recipient, sender);
                udpConnectionInfo.getAgentUdpChannel().writeAndFlush(udpPackage);
            }
        }
    }
}
