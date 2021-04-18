package com.ppaass.agent.business.socks;

import com.ppaass.agent.IAgentConst;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
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
class SocksAgentReceiveProxyDataHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    private final SocksAgentSendTcpDataToProxyHandler socksAgentSendTcpDataToProxyHandler;

    SocksAgentReceiveProxyDataHandler(
            SocksAgentSendTcpDataToProxyHandler socksAgentSendTcpDataToProxyHandler) {
        this.socksAgentSendTcpDataToProxyHandler = socksAgentSendTcpDataToProxyHandler;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext proxyChannelContext, Throwable cause) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        PpaassLogger.INSTANCE.error(() -> "Proxy channel exception happen, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText(), cause});
        var agentChannel = proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.AGENT_CHANNEL).get();
        if (agentChannel != null) {
            agentChannel.close();
        }
        proxyChannel.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext proxyChannelContext) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        PpaassLogger.INSTANCE.error(() -> "Proxy channel closed, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText()});
        var agentChannel = proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.AGENT_CHANNEL).get();
        if (agentChannel != null) {
            agentChannel.close();
        }
        var channelPool = proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.CHANNEL_POOL).get();
        channelPool.invalidateObject(proxyChannel);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannel = proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.AGENT_CHANNEL).get();
        switch (proxyMessage.getBody().getBodyType()) {
            case TCP_CONNECT_SUCCESS -> {
                handleTcpConnectSuccess(proxyMessage, proxyChannel, agentChannel);
            }
            case TCP_CONNECT_FAIL -> {
                handleTcpConnectFail(proxyMessage, proxyChannel, agentChannel);
            }
            case TCP_DATA_FAIL -> {
                handleTcpDataFail(proxyMessage, proxyChannel, agentChannel);
            }
            case TCP_CONNECTION_CLOSE -> {
                handleTcpConnectionClose(proxyMessage, proxyChannel, agentChannel);
            }
            case TCP_DATA_SUCCESS -> {
                handleTcpDataSuccess(proxyMessage, proxyChannel, agentChannel);
            }
            case UDP_DATA_FAIL -> {
            }
            case UDP_DATA_SUCCESS -> {
                handleUdpDataSuccess(proxyMessage, proxyChannel);
            }
        }
    }

    private void handleUdpDataSuccess(ProxyMessage proxyMessage, Channel proxyChannel) {
        var udpConnectionInfo = proxyChannel.attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO).get();
        var recipient = new InetSocketAddress(udpConnectionInfo.getClientSenderHost(),
                udpConnectionInfo.getClientSenderPort());
        var sender = new InetSocketAddress(IAgentConst.LOCAL_IP_ADDRESS,
                udpConnectionInfo.getAgentUdpPort());
        var udpData = proxyMessage.getBody().getData();
        var socks5UdpResponseBuf = Unpooled.buffer();
        socks5UdpResponseBuf.writeByte(0);
        socks5UdpResponseBuf.writeByte(0);
        socks5UdpResponseBuf.writeByte(0);
        var clientRecipientHost = udpConnectionInfo.getClientRecipientHost();
        if (NetUtil.isValidIpV4Address(clientRecipientHost)) {
            socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv4.byteValue());
            try {
                Socks5AddressEncoder.DEFAULT
                        .encodeAddress(Socks5AddressType.IPv4, clientRecipientHost,
                                socks5UdpResponseBuf);
            } catch (Exception e) {
                PpaassLogger.INSTANCE.error(() -> "Fail to write udp message back to agent because of exception(1).",
                        () -> new Object[]{e});
                return;
            }
        } else {
            if (NetUtil.isValidIpV6Address(clientRecipientHost)) {
                socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv6.byteValue());
                try {
                    Socks5AddressEncoder.DEFAULT
                            .encodeAddress(Socks5AddressType.IPv6, clientRecipientHost,
                                    socks5UdpResponseBuf);
                } catch (Exception e) {
                    PpaassLogger.INSTANCE
                            .error(() -> "Fail to write udp message back to agent because of exception(2).",
                                    () -> new Object[]{e});
                    return;
                }
            } else {
                socks5UdpResponseBuf.writeByte(clientRecipientHost.length());
                try {
                    Socks5AddressEncoder.DEFAULT
                            .encodeAddress(Socks5AddressType.DOMAIN,
                                    clientRecipientHost,
                                    socks5UdpResponseBuf);
                } catch (Exception e) {
                    PpaassLogger.INSTANCE
                            .error(() -> "Fail to write udp message back to agent because of exception(3).",
                                    () -> new Object[]{e});
                    return;
                }
            }
        }
        socks5UdpResponseBuf.writeShort(udpConnectionInfo.getClientRecipientPort());
        socks5UdpResponseBuf.writeBytes(udpData);
        var udpPackageSendToAgent =
                new DatagramPacket(socks5UdpResponseBuf, recipient, sender);
        udpConnectionInfo.getAgentUdpChannel().writeAndFlush(udpPackageSendToAgent);
    }

    private void handleTcpDataSuccess(ProxyMessage proxyMessage, Channel proxyChannel,
                                      Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_DATA_SUCCESS, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        if (agentTcpChannel == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No agent channel attached to proxy channel, ignore the TCP_DATA_SUCCESS, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyMessage
                    });
            return;
        }
        var tcpConnectionInfo =
                agentTcpChannel.attr(ISocksAgentConst.IAgentChannelAttr.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No tcp connection info attach to agent channel, close the agent channel on TCP_DATA_SUCCESS, agent channel = {}, proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.close();
            return;
        }
        if (!tcpConnectionInfo.getTargetHost().equals(proxyMessage.getBody().getTargetHost()) ||
                tcpConnectionInfo.getTargetPort() != proxyMessage.getBody().getTargetPort()) {
            PpaassLogger.INSTANCE.error(
                    () -> "Tcp connection info attach to agent channel is different from the proxy message, close the agent channel on TCP_DATA_SUCCESS, agent channel = {},  proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.close();
            return;
        }
        var tcpDataByteBuf = Unpooled.wrappedBuffer(proxyMessage.getBody().getData());
        agentTcpChannel.writeAndFlush(
                tcpDataByteBuf)
                .addListener((ChannelFutureListener) agentChannelFuture -> {
                    if (agentChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE.trace(SocksAgentReceiveProxyDataHandler.class,
                                () -> "Forward proxy data to client success [TCP_DATA_SUCCESS], agent channel = {},  proxy channel = {}",
                                () -> new Object[]{
                                        agentTcpChannel.id().asLongText(),
                                        proxyChannel.id().asLongText()
                                });
                        return;
                    }
                    PpaassLogger.INSTANCE.trace(SocksAgentReceiveProxyDataHandler.class,
                            () -> "Forward proxy data to client fail [TCP_DATA_SUCCESS], close it, agent channel = {},  proxy channel = {}",
                            () -> new Object[]{
                                    agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                            });
                    agentTcpChannel.close();
                });
    }

    private void handleTcpConnectionClose(ProxyMessage proxyMessage, Channel proxyChannel,
                                          Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECTION_CLOSE, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        if (agentTcpChannel == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No agent channel attached to proxy channel, ignore the TCP_CONNECTION_CLOSE, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyMessage
                    });
            return;
        }
        PpaassLogger.INSTANCE.info(
                () -> "Remove agent channel from agent channel map on proxy channel as receive TCP_CONNECTION_CLOSE, agent channel={}, proxy channel={}, proxy message:\n{}\n",
                () -> new Object[]{
                        agentTcpChannel.id().asLongText(),
                        proxyChannel.id().asLongText(),
                        proxyMessage
                });
        var tcpConnectionInfo =
                agentTcpChannel.attr(ISocksAgentConst.IAgentChannelAttr.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No tcp connection info attach to agent channel, close the agent channel on TCP_CONNECTION_CLOSE, agent channel={} proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.close();
            return;
        }
        if (!tcpConnectionInfo.getTargetHost().equals(proxyMessage.getBody().getTargetHost()) ||
                tcpConnectionInfo.getTargetPort() != proxyMessage.getBody().getTargetPort()) {
            PpaassLogger.INSTANCE.error(
                    () -> "Tcp connection info attach to agent channel is different from the proxy message, close the agent channel on TCP_CONNECTION_CLOSE, agent channel={}, proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.close();
            return;
        }
        PpaassLogger.INSTANCE.info(
                () -> "Close agent channel on receive TCP_CONNECTION_CLOSE, agent channel = {},  proxy channel = {}",
                () -> new Object[]{
                        agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                });
        agentTcpChannel.close();
    }

    private void handleTcpDataFail(ProxyMessage proxyMessage, Channel proxyChannel,
                                   Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_DATA_FAIL, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        if (agentTcpChannel == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No agent channel attached to proxy channel, ignore the TCP_DATA_FAIL, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyMessage
                    });
            return;
        }
        var tcpConnectionInfo =
                agentTcpChannel.attr(ISocksAgentConst.IAgentChannelAttr.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No tcp connection info attach to agent channel, close the agent channel on TCP_DATA_FAIL, agent channel={}, proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.close();
            return;
        }
        if (!tcpConnectionInfo.getTargetHost().equals(proxyMessage.getBody().getTargetHost()) ||
                tcpConnectionInfo.getTargetPort() != proxyMessage.getBody().getTargetPort()) {
            PpaassLogger.INSTANCE.error(
                    () -> "Tcp connection info attach to agent channel is different from the proxy message, close the agent channel on TCP_DATA_FAIL, agent channel={}, proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.close();
            return;
        }
        PpaassLogger.INSTANCE.info(
                () -> "Close agent channel on receive TCP_DATA_FAIL, agent channel = {},  proxy channel = {}",
                () -> new Object[]{
                        agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                });
        agentTcpChannel.close();
    }

    private void handleTcpConnectFail(ProxyMessage proxyMessage, Channel proxyChannel,
                                      Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECT_FAIL, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        if (agentTcpChannel == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No agent channel attached to proxy channel, ignore the TCP_CONNECT_FAIL, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyMessage
                    });
            return;
        }
        var tcpConnectionInfo =
                agentTcpChannel.attr(ISocksAgentConst.IAgentChannelAttr.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No tcp connection info attach to agent channel, close the agent channel on TCP_CONNECT_FAIL, agent channel={}, proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.close();
            return;
        }
        if (!tcpConnectionInfo.getTargetHost().equals(proxyMessage.getBody().getTargetHost()) ||
                tcpConnectionInfo.getTargetPort() != proxyMessage.getBody().getTargetPort()) {
            PpaassLogger.INSTANCE.error(
                    () -> "Tcp connection info attach to agent channel is different from the proxy message, close the agent channel on TCP_CONNECT_FAIL, agent channel={}, proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.close();
            return;
        }
        PpaassLogger.INSTANCE.debug(
                () -> "Found connection information in agent channel [TCP_CONNECT_FAIL], agent channel = {},  proxy channel = {}",
                () -> new Object[]{
                        agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                });
        agentTcpChannel.writeAndFlush(
                new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                        tcpConnectionInfo.getTargetAddressType()))
                .addListener((ChannelFutureListener) agentChannelFuture -> {
                    PpaassLogger.INSTANCE.info(
                            () -> "Close agent channel on receive TCP_CONNECT_FAIL, agent channel = {},  proxy channel = {}",
                            () -> new Object[]{
                                    agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                            });
                    agentTcpChannel.close();
                });
    }

    private void handleTcpConnectSuccess(ProxyMessage proxyMessage, Channel proxyChannel,
                                         Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECT_SUCCESS, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        if (agentTcpChannel == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No agent channel attached to proxy channel, ignore the TCP_CONNECT_SUCCESS, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyMessage
                    });
            return;
        }
        var tcpConnectionInfo =
                agentTcpChannel.attr(ISocksAgentConst.IAgentChannelAttr.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No tcp connection info attach to agent channel, close the agent channel, agent channel={}, proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.writeAndFlush(
                    new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4,
                            proxyMessage.getBody().getTargetHost(), proxyMessage.getBody().getTargetPort()))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (!tcpConnectionInfo.getTargetHost().equals(proxyMessage.getBody().getTargetHost()) ||
                tcpConnectionInfo.getTargetPort() != proxyMessage.getBody().getTargetPort()) {
            PpaassLogger.INSTANCE.error(
                    () -> "Tcp connection info attach to agent channel is different from the proxy message, close the agent channel, agent channel={}, proxy message:\n{}\n",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            proxyMessage
                    });
            agentTcpChannel.writeAndFlush(
                    new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4,
                            proxyMessage.getBody().getTargetHost(), proxyMessage.getBody().getTargetPort()))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        agentTcpChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                tcpConnectionInfo.getTargetAddressType(),
                tcpConnectionInfo.getTargetHost(), tcpConnectionInfo.getTargetPort()))
                .addListener((ChannelFutureListener) agentChannelFuture -> {
                    if (agentChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE.debug(
                                () -> "Success to send socks5 SUCCESS to client, read more from client channel,agent channel = {},  proxy channel = {}",
                                () -> new Object[]{
                                        agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                                });
                        agentTcpChannel.pipeline().addBefore(IAgentConst.LAST_INBOUND_HANDLER,
                                SocksAgentEntryHandler.class.getName(),
                                this.socksAgentSendTcpDataToProxyHandler);
                        return;
                    }
                    PpaassLogger.INSTANCE.debug(
                            () -> "Fail to send socks5 SUCCESS to client, close client channel, agent channel = {},  proxy channel = {}",
                            () -> new Object[]{
                                    agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                            });
                    agentTcpChannel.close();
                });
    }
}
