package com.ppaass.agent.business.sa;

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
import org.apache.commons.pool2.DestroyMode;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
@Service
class SAReceiveProxyDataHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    private final SASendTcpDataToProxyHandler saSendTcpDataToProxyHandler;

    SAReceiveProxyDataHandler(
            SASendTcpDataToProxyHandler saSendTcpDataToProxyHandler) {
        this.saSendTcpDataToProxyHandler = saSendTcpDataToProxyHandler;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext proxyChannelContext, Throwable cause) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        PpaassLogger.INSTANCE.error(() -> "Proxy channel exception happen, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText(), cause});
        proxyChannel.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext proxyChannelContext) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannelsOnProxyChannel = proxyChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
        agentChannelsOnProxyChannel.forEach((agentChannelId, agentChannelWrapper) -> {
            if(agentChannelWrapper.isClosed()){
                return;
            }
            PpaassLogger.INSTANCE.error(() -> "Proxy channel closed, proxy channel = {}, agent channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText(),
                            agentChannelWrapper.getChannel().id().asLongText()});
            agentChannelWrapper.markClose();
        });
        var channelPool = proxyChannel.attr(ISAConstant.IProxyChannelConstant.CHANNEL_POOL).get();
        proxyChannel.attr(ISAConstant.IProxyChannelConstant.CLOSED_ALREADY).set(true);
        channelPool.invalidateObject(proxyChannel, DestroyMode.ABANDONED);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannelsOnProxyChannel = proxyChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
        var agentChannelWrapper = agentChannelsOnProxyChannel.get(proxyMessage.getBody().getAgentChannelId());
        if (agentChannelWrapper == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "The agent channel id in proxy message is not for current proxy channel, discard the proxy message, proxy channel = {}, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyChannel.id().asLongText(),
                            proxyMessage
                    });
            return;
        }
        switch (proxyMessage.getBody().getBodyType()) {
            case TCP_CONNECT_SUCCESS -> {
                handleTcpConnectSuccess(proxyMessage, proxyChannel, agentChannelWrapper.getChannel());
            }
            case TCP_CONNECT_FAIL -> {
                handleTcpConnectFail(proxyMessage, proxyChannel, agentChannelWrapper.getChannel());
            }
            case TCP_CONNECTION_CLOSE -> {
                handleTcpConnectionClose(proxyMessage, proxyChannel, agentChannelWrapper.getChannel());
            }
            case TCP_DATA_SUCCESS -> {
                handleTcpDataSuccess(proxyMessage, proxyChannel, agentChannelWrapper.getChannel());
            }
            case UDP_DATA_SUCCESS -> {
                handleUdpDataSuccess(proxyMessage, proxyChannel);
            }
            case TCP_DATA_FAIL, UDP_DATA_FAIL -> {
                handleTcpDataFail(proxyMessage, proxyChannel, agentChannelWrapper.getChannel());
            }
        }
    }

    private void handleTcpDataSuccess(ProxyMessage proxyMessage, Channel proxyChannel,
                                      Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_DATA_SUCCESS, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        var tcpDataByteBuf = Unpooled.wrappedBuffer(proxyMessage.getBody().getData());
        agentTcpChannel.writeAndFlush(
                tcpDataByteBuf)
                .addListener((ChannelFutureListener) agentChannelFuture -> {
                    if (agentChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE.trace(SAReceiveProxyDataHandler.class,
                                () -> "Forward proxy data to client success [TCP_DATA_SUCCESS], agent channel = {},  proxy channel = {}",
                                () -> new Object[]{
                                        agentTcpChannel.id().asLongText(),
                                        proxyChannel.id().asLongText()
                                });
                        return;
                    }
                    PpaassLogger.INSTANCE.trace(SAReceiveProxyDataHandler.class,
                            () -> "Forward proxy data to client fail [TCP_DATA_SUCCESS], close it, agent channel = {},  proxy channel = {}",
                            () -> new Object[]{
                                    agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                            });
                    agentTcpChannel.close();
                });
    }

    private void handleUdpDataSuccess(ProxyMessage proxyMessage, Channel proxyChannel) {
        var udpConnectionInfo = proxyChannel.attr(ISAConstant.SOCKS_UDP_CONNECTION_INFO).get();
        if (udpConnectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "No udp connection info attach to agent channel, close the agent channel on UDP_DATA_SUCCESS, proxy channel = {}, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyChannel.id().asLongText(),
                            proxyMessage
                    });
            return;
        }
        var recipient = new InetSocketAddress(udpConnectionInfo.getClientSenderHost(),
                udpConnectionInfo.getClientSenderPort());
        var sender = new InetSocketAddress(ISAConstant.LOCAL_IP_ADDRESS,
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

    private void handleTcpConnectionClose(ProxyMessage proxyMessage, Channel proxyChannel,
                                          Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECTION_CLOSE, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
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
        agentTcpChannel.close();
    }

    private void handleTcpConnectFail(ProxyMessage proxyMessage, Channel proxyChannel,
                                      Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECT_FAIL, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        var addrType = SAUtil.INSTANCE.parseAddrType(proxyMessage.getBody().getTargetHost());
        agentTcpChannel.writeAndFlush(
                new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                        addrType))
                .addListener((ChannelFutureListener) agentChannelFuture -> {
                    PpaassLogger.INSTANCE.info(
                            () -> "Close agent channel on receive TCP_CONNECT_FAIL, agent channel = {},  proxy channel = {}",
                            () -> new Object[]{
                                    agentTcpChannel.id().asLongText(),
                                    proxyChannel.id().asLongText()
                            });
                    agentTcpChannel.close();
                });
    }

    private void handleTcpConnectSuccess(ProxyMessage proxyMessage, Channel proxyChannel, Channel agentTcpChannel) {
        PpaassLogger.INSTANCE.debug(
                () -> "Receive TCP_CONNECT_SUCCESS, proxy message:\n{}\n",
                () -> new Object[]{
                        proxyMessage
                });
        var addrType = SAUtil.INSTANCE.parseAddrType(proxyMessage.getBody().getTargetHost());
        agentTcpChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                addrType,
                proxyMessage.getBody().getTargetHost(), proxyMessage.getBody().getTargetPort()))
                .addListener((ChannelFutureListener) agentChannelFuture -> {
                    if (agentChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE.debug(
                                () -> "Success to send socks5 SUCCESS to client, read more from client channel,agent channel = {},  proxy channel = {}",
                                () -> new Object[]{
                                        agentTcpChannel.id().asLongText(), proxyChannel.id().asLongText()
                                });
                        agentTcpChannel.pipeline().addBefore(IAgentConst.LAST_INBOUND_HANDLER,
                                SAEntryHandler.class.getName(),
                                this.saSendTcpDataToProxyHandler);
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
