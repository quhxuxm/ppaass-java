package com.ppaass.agent.handler.socks;

import com.ppaass.agent.IAgentConst;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.ProxyMessage;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.NetUtil;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
@Service
class SocksAgentP2ATcpChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    static {
        PpaassLogger.INSTANCE.register(SocksAgentP2ATcpChannelHandler.class);
    }

    private final SocksAgentA2PTcpChannelHandler socksAgentA2PTcpChannelHandler;

    SocksAgentP2ATcpChannelHandler(
            SocksAgentA2PTcpChannelHandler socksAgentA2PTcpChannelHandler) {
        this.socksAgentA2PTcpChannelHandler = socksAgentA2PTcpChannelHandler;
    }

    @Override
    public void channelActive(ChannelHandlerContext proxyChannelContext) throws Exception {
        super.channelActive(proxyChannelContext);
        var proxyChannel = proxyChannelContext.channel();
        proxyChannel.read();
        var tcpConnectionInfo = proxyChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo != null) {
            var agentChannel = tcpConnectionInfo.getAgentTcpChannel();
            agentChannel.read();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext proxyChannelContext) throws Exception {
        super.channelReadComplete(proxyChannelContext);
        var proxyChannel = proxyChannelContext.channel();
        proxyChannel.read();
        var tcpConnectionInfo = proxyChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo != null) {
            var agentChannel = tcpConnectionInfo.getAgentTcpChannel();
            if (agentChannel.isWritable()) {
                proxyChannel.read();
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var tcpConnectionInfo = proxyChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO).get();
        switch (proxyMessage.getBody().getBodyType()) {
            case CONNECT_SUCCESS -> {
                PpaassLogger.INSTANCE.debug(SocksAgentP2ATcpChannelHandler.class,
                        () -> "Receive CONNECT_SUCCESS from proxy, proxy channel = {}", () -> new Object[]{
                                proxyChannel.id().asLongText()
                        });
                if (tcpConnectionInfo == null) {
                    PpaassLogger.INSTANCE.debug(SocksAgentP2ATcpChannelHandler.class,
                            () -> "No tcp connection information in proxy channel [CONNECT_SUCCESS], close it, proxy channel = {}",
                            () -> new Object[]{
                                    proxyChannel.id().asLongText()
                            });
                    proxyChannel.close();
                    return;
                }
                var agentTcpChannel = tcpConnectionInfo.getAgentTcpChannel();
                PpaassLogger.INSTANCE.debug(SocksAgentP2ATcpChannelHandler.class,
                        () -> "Found connection information in proxy channel [CONNECT_SUCCESS], agent channel = {},  proxy channel = {}",
                        () -> new Object[]{
                                agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
                agentTcpChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                        tcpConnectionInfo.getTargetAddressType(), tcpConnectionInfo.getTargetHost(),
                        tcpConnectionInfo.getTargetPort()))
                        .addListener((ChannelFutureListener) agentChannelFuture -> {
                            if (agentChannelFuture.isSuccess()) {
                                PpaassLogger.INSTANCE.debug(SocksAgentP2ATcpChannelHandler.class,
                                        () -> "Success to send socks5 SUCCESS to client, read more from client channel,agent channel = {},  proxy channel = {}",
                                        () -> new Object[]{
                                                agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                                        });
                                agentTcpChannel.pipeline().addBefore(IAgentConst.LAST_INBOUND_HANDLER,
                                        SocksAgentProtocolHandler.class.getName(),
                                        this.socksAgentA2PTcpChannelHandler);
                                agentTcpChannel.read();
                                proxyChannel.read();
                                return;
                            }
                            PpaassLogger.INSTANCE.debug(SocksAgentP2ATcpChannelHandler.class,
                                    () -> "Fail to send socks5 SUCCESS to client, close client channel, agent channel = {},  proxy channel = {}",
                                    () -> new Object[]{
                                            agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                                    });
                            agentTcpChannel.close();
                            proxyChannel.close();
                        });
            }
            case CONNECT_FAIL -> {
                PpaassLogger.INSTANCE.error(SocksAgentP2ATcpChannelHandler.class,
                        () -> "Receive CONNECT_FAIL from proxy, proxy channel = {}",
                        () -> new Object[]{
                                proxyChannel.id().asLongText()
                        });
                if (tcpConnectionInfo == null) {
                    PpaassLogger.INSTANCE.error(SocksAgentP2ATcpChannelHandler.class,
                            () -> "No tcp connection information in proxy channel [CONNECT_FAIL], close it, proxy channel = {}",
                            () -> new Object[]{
                                    proxyChannel.id().asLongText()
                            });
                    proxyChannel.close();
                    return;
                }
                var agentTcpChannel = tcpConnectionInfo.getAgentTcpChannel();
                PpaassLogger.INSTANCE.error(SocksAgentP2ATcpChannelHandler.class,
                        () -> "Found connection information in proxy channel [CONNECT_FAIL], agent channel = {},  proxy channel = {}",
                        () -> new Object[]{
                                agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
                agentTcpChannel.writeAndFlush(
                        new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                                tcpConnectionInfo.getTargetAddressType()))
                        .addListener((ChannelFutureListener) agentChannelFuture -> {
                            PpaassLogger.INSTANCE.error(SocksAgentP2ATcpChannelHandler.class,
                                    () -> "Close connection between client and agent [CONNECT_FAIL], agent channel = {},  proxy channel = {}",
                                    () -> new Object[]{
                                            agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                                    });
                            agentTcpChannel.close();
                            proxyChannel.close();
                        });
            }
            case FAIL_TCP, FAIL_UDP -> {
                if (tcpConnectionInfo == null) {
                    PpaassLogger.INSTANCE.debug(SocksAgentP2ATcpChannelHandler.class,
                            () -> "No tcp connection information in proxy channel [{}], close it, proxy channel = {}",
                            () -> new Object[]{
                                    proxyMessage.getBody().getBodyType(),
                                    proxyChannel.id().asLongText()
                            });
                    proxyChannel.close();
                    return;
                }
                var agentTcpChannel = tcpConnectionInfo.getAgentTcpChannel();
                PpaassLogger.INSTANCE.debug(SocksAgentP2ATcpChannelHandler.class,
                        () -> "{} happen close connection, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                proxyMessage.getBody().getBodyType(),
                                agentTcpChannel.id().asLongText(),
                                proxyChannel.id().asLongText()
                        });
                proxyChannel.close();
                agentTcpChannel.close();
            }
            case OK_TCP -> {
                var tcpDataByteBuf = Unpooled.wrappedBuffer(proxyMessage.getBody().getData());
                PpaassLogger.INSTANCE.trace(SocksAgentP2ATcpChannelHandler.class,
                        () -> "Receive OK_TCP from proxy, proxy channel = {}, tcp data:\n{}\n",
                        () -> new Object[]{
                                proxyChannel.id().asLongText(),
                                ByteBufUtil.prettyHexDump(tcpDataByteBuf)
                        });
                if (tcpConnectionInfo == null) {
                    PpaassLogger.INSTANCE.error(SocksAgentP2ATcpChannelHandler.class,
                            () -> "No tcp connection information in proxy channel [OK_TCP], close it, proxy channel = {}",
                            () -> new Object[]{
                                    proxyChannel.id().asLongText()
                            });
                    proxyChannel.close();
                    return;
                }
                var agentTcpChannel = tcpConnectionInfo.getAgentTcpChannel();
                PpaassLogger.INSTANCE.trace(SocksAgentP2ATcpChannelHandler.class,
                        () -> "Found connection information in proxy channel [OK_TCP], agent channel = {},  proxy channel = {}, tcp data:\n{}\n",
                        () -> new Object[]{
                                agentTcpChannel.id().asLongText(),
                                proxyChannel.id().asLongText(),
                                ByteBufUtil.prettyHexDump(tcpDataByteBuf)
                        });
                agentTcpChannel.writeAndFlush(
                        tcpDataByteBuf)
                        .addListener((ChannelFutureListener) agentChannelFuture -> {
                            if (agentChannelFuture.isSuccess()) {
                                PpaassLogger.INSTANCE.trace(SocksAgentP2ATcpChannelHandler.class,
                                        () -> "Forward proxy data to client success [OK_TCP], agent channel = {},  proxy channel = {}",
                                        () -> new Object[]{
                                                agentTcpChannel.id().asLongText(),
                                                proxyChannel.id().asLongText()
                                        });
                                proxyChannel.read();
                                return;
                            }
                            PpaassLogger.INSTANCE.trace(SocksAgentP2ATcpChannelHandler.class,
                                    () -> "Forward proxy data to client fail [OK_TCP], close it, agent channel = {},  proxy channel = {}",
                                    () -> new Object[]{
                                            agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                                    });
                            agentTcpChannel.close();
                            proxyChannel.close();
                        });
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
                udpConnectionInfo.getAgentUdpChannel().writeAndFlush(udpPackage)
                        .addListener((ChannelFutureListener) agentChannelFuture -> {
                            proxyChannel.read();
                        });
            }
        }
    }
}
