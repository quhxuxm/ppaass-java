package com.ppaass.agent.handler.socks;

import com.ppaass.agent.IAgentConst;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentMap;

@ChannelHandler.Sharable
@Service
class SocksAgentP2ATcpChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    private final SocksAgentA2PTcpChannelHandler socksAgentA2PTcpChannelHandler;

    SocksAgentP2ATcpChannelHandler(
            SocksAgentA2PTcpChannelHandler socksAgentA2PTcpChannelHandler) {
        this.socksAgentA2PTcpChannelHandler = socksAgentA2PTcpChannelHandler;
    }

    @Override
    public void channelInactive(ChannelHandlerContext proxyChannelContext) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannels = proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
        agentChannels.forEach((agentChannelId, channel) -> {
            PpaassLogger.INSTANCE
                    .info(() -> "Proxy channel closed, clear agent channel attached to it, agent channel = {}, proxy channel = {}",
                            () -> new Object[]{agentChannelId, proxyChannel.id().asLongText()});
            channel.close();
            agentChannels.remove(agentChannelId);
        });
        PpaassLogger.INSTANCE.info(() -> "Proxy channel closed, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText()});
        ChannelPool channelPool = proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.CHANNEL_POOL).get();
        channelPool.release(proxyChannel);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannels = proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
        switch (proxyMessage.getBody().getBodyType()) {
            case TCP_CONNECT_SUCCESS -> {
                handleTcpConnectSuccess(proxyMessage, proxyChannel, agentChannels);
            }
            case TCP_CONNECT_FAIL -> {
                handleTcpConnectFail(proxyMessage, proxyChannel, agentChannels);
            }
            case TCP_DATA_FAIL -> {
                handleTcpDataFail(proxyMessage, proxyChannel, agentChannels);
            }
            case TCP_CONNECTION_CLOSE -> {
                handleTcpConnectionClose(proxyMessage, proxyChannel, agentChannels);
            }
            case TCP_DATA_SUCCESS -> {
                handleTcpDataSuccess(proxyMessage, proxyChannel, agentChannels);
            }
//            case OK_UDP -> {
//                var udpMessageContent = MessageSerializer.JSON_OBJECT_MAPPER.readValue(proxyMessage.getBody().getData(),
//                        UdpTransferMessageContent.class);
//                var udpConnectionInfo = proxyChannel.attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO).get();
//                var recipient = new InetSocketAddress(udpConnectionInfo.getClientSenderHost(),
//                        udpConnectionInfo.getClientSenderPort());
//                var sender = new InetSocketAddress(IAgentConst.LOCAL_IP_ADDRESS,
//                        udpConnectionInfo.getAgentUdpPort());
//                var udpData = udpMessageContent.getData();
//                var socks5UdpResponseBuf = Unpooled.buffer();
//                socks5UdpResponseBuf.writeByte(0);
//                socks5UdpResponseBuf.writeByte(0);
//                socks5UdpResponseBuf.writeByte(0);
//                var clientRecipientHost = udpConnectionInfo.getClientRecipientHost();
//                if (NetUtil.isValidIpV4Address(clientRecipientHost)) {
//                    socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv4.byteValue());
//                    Socks5AddressEncoder.DEFAULT
//                            .encodeAddress(Socks5AddressType.IPv4, clientRecipientHost,
//                                    socks5UdpResponseBuf);
//                } else {
//                    if (NetUtil.isValidIpV6Address(clientRecipientHost)) {
//                        socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv6.byteValue());
//                        Socks5AddressEncoder.DEFAULT
//                                .encodeAddress(Socks5AddressType.IPv6, clientRecipientHost,
//                                        socks5UdpResponseBuf);
//                    } else {
//                        socks5UdpResponseBuf.writeByte(clientRecipientHost.length());
//                        Socks5AddressEncoder.DEFAULT
//                                .encodeAddress(Socks5AddressType.DOMAIN,
//                                        clientRecipientHost,
//                                        socks5UdpResponseBuf);
//                    }
//                }
//                socks5UdpResponseBuf.writeShort(udpConnectionInfo.getClientRecipientPort());
//                socks5UdpResponseBuf.writeBytes(udpData);
//                var udpPackage = new DatagramPacket(socks5UdpResponseBuf, recipient, sender);
//                udpConnectionInfo.getAgentUdpChannel().writeAndFlush(udpPackage)
//                        .addListener((ChannelFutureListener) agentChannelFuture -> {
//                            proxyChannel.read();
//                        });
//            }
        }
    }

    private void handleTcpDataSuccess(ProxyMessage proxyMessage, Channel proxyChannel,
                                      ConcurrentMap<String, Channel> agentChannels) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_DATA_SUCCESS, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        var agentTcpChannel = agentChannels.get(proxyMessage.getBody().getAgentChannelId());
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
                        PpaassLogger.INSTANCE.trace(SocksAgentP2ATcpChannelHandler.class,
                                () -> "Forward proxy data to client success [TCP_DATA_SUCCESS], agent channel = {},  proxy channel = {}",
                                () -> new Object[]{
                                        agentTcpChannel.id().asLongText(),
                                        proxyChannel.id().asLongText()
                                });
                        return;
                    }
                    PpaassLogger.INSTANCE.trace(SocksAgentP2ATcpChannelHandler.class,
                            () -> "Forward proxy data to client fail [TCP_DATA_SUCCESS], close it, agent channel = {},  proxy channel = {}",
                            () -> new Object[]{
                                    agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                            });
                    agentTcpChannel.close();
                });
    }

    private void handleTcpConnectionClose(ProxyMessage proxyMessage, Channel proxyChannel,
                                          ConcurrentMap<String, Channel> agentChannels) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECTION_CLOSE, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        var agentTcpChannel = agentChannels.get(proxyMessage.getBody().getAgentChannelId());
        if (agentTcpChannel == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No agent channel attached to proxy channel, ignore the TCP_CONNECTION_CLOSE, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyMessage
                    });
            return;
        }
        agentChannels.remove(agentTcpChannel.id().asLongText());
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
                () -> "Found connection information in agent channel [TCP_CONNECTION_CLOSE], agent channel = {},  proxy channel = {}",
                () -> new Object[]{
                        agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                });
        agentTcpChannel.close();
    }

    private void handleTcpDataFail(ProxyMessage proxyMessage, Channel proxyChannel,
                                   ConcurrentMap<String, Channel> agentChannels) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_DATA_FAIL, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        var agentTcpChannel = agentChannels.get(proxyMessage.getBody().getAgentChannelId());
        if (agentTcpChannel == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No agent channel attached to proxy channel, ignore the TCP_DATA_FAIL, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyMessage
                    });
            return;
        }
        agentChannels.remove(agentTcpChannel.id().asLongText());
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
        PpaassLogger.INSTANCE.error(
                () -> "Found connection information in agent channel [TCP_DATA_FAIL], agent channel = {},  proxy channel = {}",
                () -> new Object[]{
                        agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                });
        agentTcpChannel.close();
    }

    private void handleTcpConnectFail(ProxyMessage proxyMessage, Channel proxyChannel,
                                      ConcurrentMap<String, Channel> agentChannels) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECT_FAIL, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        var agentTcpChannel = agentChannels.get(proxyMessage.getBody().getAgentChannelId());
        if (agentTcpChannel == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No agent channel attached to proxy channel, ignore the TCP_CONNECT_FAIL, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyMessage
                    });
            return;
        }
        agentChannels.remove(agentTcpChannel.id().asLongText());
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
                    PpaassLogger.INSTANCE.error(SocksAgentP2ATcpChannelHandler.class,
                            () -> "Close connection between client and agent [TCP_CONNECT_FAIL], agent channel = {},  proxy channel = {}",
                            () -> new Object[]{
                                    agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                            });
                    agentTcpChannel.close();
                });
    }

    private void handleTcpConnectSuccess(ProxyMessage proxyMessage, Channel proxyChannel,
                                         ConcurrentMap<String, Channel> agentChannels) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECT_SUCCESS, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        var agentTcpChannel = agentChannels.get(proxyMessage.getBody().getAgentChannelId());
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
                                this.socksAgentA2PTcpChannelHandler);
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
