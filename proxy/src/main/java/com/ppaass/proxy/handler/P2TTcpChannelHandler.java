package com.ppaass.proxy.handler;

import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.*;
import com.ppaass.proxy.IProxyConstant;
import com.ppaass.proxy.handler.bo.TcpConnectionInfo;
import com.ppaass.proxy.handler.bo.UdpConnectionInfo;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

@Service
@ChannelHandler.Sharable
public class P2TTcpChannelHandler extends SimpleChannelInboundHandler<AgentMessage> {
    static {
        PpaassLogger.INSTANCE.register(P2TTcpChannelHandler.class);
    }

    private final Bootstrap targetTcpBootstrap;
    private final Bootstrap targetUdpBootstrap;

    public P2TTcpChannelHandler(Bootstrap targetTcpBootstrap, Bootstrap targetUdpBootstrap) {
        this.targetTcpBootstrap = targetTcpBootstrap;
        this.targetUdpBootstrap = targetUdpBootstrap;
    }

    @Override
    public void channelActive(ChannelHandlerContext proxyChannelContext) throws Exception {
        super.channelActive(proxyChannelContext);
        proxyChannelContext.read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext proxyChannelContext) throws Exception {
        super.channelReadComplete(proxyChannelContext);
        var proxyChannel = proxyChannelContext.channel();
        var connectionInfo =
                proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
        if (connectionInfo != null) {
            var targetTcpChannel = connectionInfo.getTargetTcpChannel();
            if (targetTcpChannel.isWritable()) {
                proxyChannel.read();
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentMessageBodyType = agentMessage.getBody().getBodyType();
        switch (agentMessageBodyType) {
            case TCP_DATA -> {
                var connectionInfo =
                        proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
                if (connectionInfo == null) {
                    PpaassLogger.INSTANCE.error(P2TTcpChannelHandler.class,
                            () -> "Fail to transfer data from proxy to target because of no agent connection information attached, proxy channel = {}.",
                            () -> new Object[]{
                                    proxyChannel.id().asLongText()
                            });
                    proxyChannel.close();
                    return;
                }
                var targetTcpChannel = connectionInfo.getTargetTcpChannel();
                targetTcpChannel.writeAndFlush(
                        Unpooled.wrappedBuffer(agentMessage.getBody().getData()))
                        .addListener((ChannelFutureListener) targetChannelFuture -> {
                            if (targetChannelFuture.isSuccess()) {
                                proxyChannel.read();
                                //targetTcpChannel.read();
                                return;
                            }
                            PpaassLogger.INSTANCE.error(P2TTcpChannelHandler.class,
                                    () -> "Fail to write agent message to target because of exception, proxy channel = {}, target channel = {}",
                                    () -> new Object[]{
                                            proxyChannel.id().asLongText(), targetTcpChannel.id().asLongText(),
                                            targetChannelFuture.cause()
                                    });
                            var failProxyMessageBody = new ProxyMessageBody(connectionInfo.getConnectionId(),
                                    agentMessage.getBody().getUserToken(), agentMessage.getBody().getTargetHost(),
                                    agentMessage.getBody().getTargetPort(), ProxyMessageBodyType.FAIL_TCP,
                                    new byte[]{});
                            var failProxyMessage = new ProxyMessage(MessageSerializer.INSTANCE.generateUuidInBytes(),
                                    EncryptionType.choose(), failProxyMessageBody);
                            targetTcpChannel.close().addListener(future -> {
                                proxyChannel.writeAndFlush(failProxyMessage).addListener(ChannelFutureListener.CLOSE);
                            });
                        });
            }
            case UDP_DATA -> {
                var udpMessageContent = MessageSerializer.JSON_OBJECT_MAPPER
                        .readValue(agentMessage.getBody().getData(), UdpTransferMessageContent.class);
                var destinationInetSocketAddress = new InetSocketAddress(udpMessageContent.getOriginalDestinationAddress(),
                        udpMessageContent.getOriginalDestinationPort());
                var udpData = Unpooled.wrappedBuffer(udpMessageContent.getData());
                var udpPackage = new DatagramPacket(udpData, destinationInetSocketAddress);
                PpaassLogger.INSTANCE.debug(P2TTcpChannelHandler.class,
                        () -> "Agent message for udp, proxy channel = {}, udp data: \n{}\n",
                        () -> new Object[]{
                                proxyChannel.id().asLongText(),
                                ByteBufUtil.prettyHexDump(udpData)
                        });
                var udpConnectionInfo = proxyChannel.attr(IProxyConstant.UDP_CONNECTION_INFO).get();
                if (udpConnectionInfo == null) {
                    var targetUdpChannel =
                            targetUdpBootstrap.bind(0).sync().channel();
                    udpConnectionInfo = new UdpConnectionInfo(
                            udpMessageContent.getOriginalDestinationAddress(),
                            udpMessageContent.getOriginalDestinationPort(),
                            udpMessageContent.getOriginalSourceAddress(),
                            udpMessageContent.getOriginalSourcePort(),
                            udpMessageContent.getOriginalAddrType(),
                            agentMessage.getBody().getUserToken(),
                            proxyChannel,
                            targetUdpChannel
                    );
                    targetUdpChannel.attr(IProxyConstant.UDP_CONNECTION_INFO).setIfAbsent(udpConnectionInfo);
                }
                PpaassLogger.INSTANCE.debug(P2TTcpChannelHandler.class,
                        () -> "Receive udp package from agent, proxy channel = {}, udp package: \n{}\n",
                        () -> new Object[]{
                                proxyChannel.id().asLongText(),
                                udpPackage
                        });
                var targetUdpChannel = udpConnectionInfo.getTargetUdpChannel();
                targetUdpChannel.writeAndFlush(udpPackage).addListener(future -> {
                    //targetUdpChannel.read();
                    proxyChannel.read();
                });
            }
            case CONNECT_WITH_KEEP_ALIVE, CONNECT_WITHOUT_KEEP_ALIVE -> {
                this.targetTcpBootstrap
                        .connect(agentMessage.getBody().getTargetHost(),
                                agentMessage.getBody().getTargetPort())
                        .addListener((ChannelFutureListener) targetChannelFuture -> {
                            if (!targetChannelFuture.isSuccess()) {
                                var proxyMessageBody = new ProxyMessageBody(
                                        agentMessage.getBody().getId(),
                                        agentMessage.getBody().getUserToken(),
                                        agentMessage.getBody().getTargetHost(),
                                        agentMessage.getBody().getTargetPort(),
                                        ProxyMessageBodyType.CONNECT_FAIL,
                                        new byte[]{});
                                var proxyMessage =
                                        new ProxyMessage(MessageSerializer.INSTANCE.generateUuidInBytes(),
                                                EncryptionType
                                                        .choose(), proxyMessageBody);
                                proxyChannel.writeAndFlush(proxyMessage).addListener(ChannelFutureListener.CLOSE);
                                return;
                            }
                            var targetChannel = targetChannelFuture.channel();
                            var connectionInfo = new TcpConnectionInfo(
                                    agentMessage.getBody().getId(),
                                    agentMessage.getBody().getTargetHost(),
                                    agentMessage.getBody().getTargetPort(),
                                    agentMessage.getBody().getUserToken(),
                                    proxyChannel,
                                    targetChannel,
                                    agentMessage.getBody().getBodyType() ==
                                            AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE
                            );
                            targetChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).setIfAbsent(connectionInfo);
                            proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).setIfAbsent(connectionInfo);
                            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE,
                                    connectionInfo.isTargetTcpConnectionKeepAlive());
                            targetChannel.config()
                                    .setOption(ChannelOption.SO_KEEPALIVE,
                                            connectionInfo.isTargetTcpConnectionKeepAlive());
                            var proxyMessageBody =
                                    new ProxyMessageBody(
                                            connectionInfo.getConnectionId(),
                                            connectionInfo.getUserToken(),
                                            connectionInfo.getTargetHost(),
                                            connectionInfo.getTargetPort(),
                                            ProxyMessageBodyType.CONNECT_SUCCESS,
                                            new byte[]{});
                            var proxyMessage =
                                    new ProxyMessage(
                                            MessageSerializer.INSTANCE.generateUuidInBytes(),
                                            EncryptionType.choose(),
                                            proxyMessageBody);
                            proxyChannel.writeAndFlush(proxyMessage)
                                    .addListener((ChannelFutureListener) proxyChannelFuture -> {
                                        if (proxyChannelFuture.isSuccess()) {
                                            proxyChannel.read();
                                            targetChannel.read();
                                            return;
                                        }
                                        PpaassLogger.INSTANCE.error(P2TTcpChannelHandler.class,
                                                () -> "Fail to write connect result message to agent because of exception, proxy channel = {}, target channel = {}",
                                                () -> new Object[]{
                                                        proxyChannel.id().asLongText(), targetChannel.id().asLongText(),
                                                        proxyChannelFuture.cause()
                                                });
                                        targetChannel.close();
                                        proxyChannel.close();
                                    });
                        });
            }
            default -> {
                PpaassLogger.INSTANCE.error(P2TTcpChannelHandler.class,
                        () -> "Fail to transfer data from proxy to target because of it is not a validate body type, proxy channel = {}.",
                        () -> new Object[]{
                                proxyChannel.id().asLongText()
                        });
                var agentTcpConnectionInfo =
                        proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
                if (agentTcpConnectionInfo != null) {
                    proxyChannel.close();
                    agentTcpConnectionInfo.getTargetTcpChannel().close();
                }
            }
        }
    }
}
