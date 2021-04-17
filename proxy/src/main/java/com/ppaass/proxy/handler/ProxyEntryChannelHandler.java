package com.ppaass.proxy.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.message.*;
import com.ppaass.proxy.IProxyConstant;
import com.ppaass.proxy.ProxyConfiguration;
import com.ppaass.proxy.handler.bo.TargetTcpInfo;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

@Service
@ChannelHandler.Sharable
public class ProxyEntryChannelHandler extends SimpleChannelInboundHandler<AgentMessage> {
    private final Bootstrap targetTcpBootstrap;
    private final ProxyConfiguration proxyConfiguration;

    public ProxyEntryChannelHandler(Bootstrap targetTcpBootstrap,
                                    ProxyConfiguration proxyConfiguration) {
        this.targetTcpBootstrap = targetTcpBootstrap;
        this.proxyConfiguration = proxyConfiguration;
    }

    private void handleTcpConnect(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) {
        var proxyChannel = proxyChannelContext.channel();
        PpaassLogger.INSTANCE.debug(() -> "Begin to create TCP connection for: {}", () -> new Object[]{agentMessage});
        this.targetTcpBootstrap
                .connect(agentMessage.getBody().getTargetHost(),
                        agentMessage.getBody().getTargetPort())
                .addListener((ChannelFutureListener) targetChannelFuture -> {
                    if (!targetChannelFuture.isSuccess()) {
                        var proxyMessageBody = new ProxyMessageBody(
                                UUIDUtil.INSTANCE.generateUuid(),
                                proxyConfiguration.getProxyInstanceId(),
                                agentMessage.getBody().getUserToken(),
                                agentMessage.getBody().getSourceHost(),
                                agentMessage.getBody().getSourcePort(),
                                agentMessage.getBody().getTargetHost(),
                                agentMessage.getBody().getTargetPort(),
                                ProxyMessageBodyType.TCP_CONNECT_FAIL,
                                agentMessage.getBody().getAgentChannelId(), null, null);
                        var proxyMessage =
                                new ProxyMessage(UUIDUtil.INSTANCE.generateUuidInBytes(),
                                        EncryptionType
                                                .choose(), proxyMessageBody);
                        proxyChannel.writeAndFlush(proxyMessage)
                                .addListener((ChannelFutureListener) proxyChannelFuture -> {
                                    if (proxyChannelFuture.isSuccess()) {
                                        PpaassLogger.INSTANCE.debug(
                                                () -> "Success to write TCP connect result (TCP_CONNECT_FAIL) to agent, agent message:\n{}\n",
                                                () -> new Object[]{
                                                        agentMessage,
                                                        proxyChannelFuture.cause()
                                                });
                                        return;
                                    }
                                    PpaassLogger.INSTANCE.error(
                                            () -> "Fail to write TCP connect result (TCP_CONNECT_FAIL) to agent because of exception, agent message:\n{}\n",
                                            () -> new Object[]{
                                                    agentMessage,
                                                    proxyChannelFuture.cause()
                                            });
                                    proxyChannel.close();
                                });
                        PpaassLogger.INSTANCE
                                .error(() -> "Fail to create TCP connection for: {}", () -> new Object[]{agentMessage});
                        return;
                    }
                    PpaassLogger.INSTANCE
                            .debug(() -> "Success to create TCP connection for: {}", () -> new Object[]{agentMessage});
                    var targetChannel = targetChannelFuture.channel();
                    var targetTcpConnectionInfo = new TargetTcpInfo(
                            agentMessage.getBody().getAgentInstanceId(),
                            agentMessage.getBody().getSourceHost(),
                            agentMessage.getBody().getSourcePort(),
                            agentMessage.getBody().getTargetHost(),
                            agentMessage.getBody().getTargetPort(),
                            agentMessage.getBody().getUserToken(),
                            agentMessage.getBody().getAgentChannelId(),
                            targetChannel.id().asLongText(),
                            proxyChannel,
                            targetChannel
                    );
                    PpaassLogger.INSTANCE
                            .debug(() -> "Create TCP connection info: \n{}\n",
                                    () -> new Object[]{targetTcpConnectionInfo});
                    targetChannel.attr(IProxyConstant.ITargetChannelAttr.TCP_INFO)
                            .setIfAbsent(targetTcpConnectionInfo);
                    proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNEL).set(targetChannel);
                    var proxyMessageBody =
                            new ProxyMessageBody(
                                    UUIDUtil.INSTANCE.generateUuid(),
                                    proxyConfiguration.getProxyInstanceId(),
                                    targetTcpConnectionInfo.getUserToken(),
                                    agentMessage.getBody().getSourceHost(),
                                    agentMessage.getBody().getSourcePort(),
                                    targetTcpConnectionInfo.getTargetHost(),
                                    targetTcpConnectionInfo.getTargetPort(),
                                    ProxyMessageBodyType.TCP_CONNECT_SUCCESS,
                                    agentMessage.getBody().getAgentChannelId(),
                                    targetChannel.id().asLongText(),
                                    null);
                    var proxyMessage =
                            new ProxyMessage(
                                    UUIDUtil.INSTANCE.generateUuidInBytes(),
                                    EncryptionType.choose(),
                                    proxyMessageBody);
                    proxyChannel.writeAndFlush(proxyMessage)
                            .addListener((ChannelFutureListener) proxyChannelFuture -> {
                                if (proxyChannelFuture.isSuccess()) {
                                    PpaassLogger.INSTANCE.debug(
                                            () -> "Success to write TCP connect result (TCP_CONNECT_SUCCESS) to agent, TCP connection info:\n{}\n",
                                            () -> new Object[]{
                                                    targetTcpConnectionInfo,
                                                    proxyChannelFuture.cause()
                                            });
                                    return;
                                }
                                PpaassLogger.INSTANCE.error(
                                        () -> "Fail to write TCP connect result (TCP_CONNECT_SUCCESS) to agent because of exception, TCP connection info:\n{}\n",
                                        () -> new Object[]{
                                                targetTcpConnectionInfo,
                                                proxyChannelFuture.cause()
                                        });
                                proxyChannel.close();
                            });
                });
    }

    private void handleTcpData(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) {
        var proxyChannel = proxyChannelContext.channel();
        var targetChannel = proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNEL).get();
        if (targetChannel == null) {
            var proxyMessageBody = new ProxyMessageBody(
                    UUIDUtil.INSTANCE.generateUuid(),
                    proxyConfiguration.getProxyInstanceId(),
                    agentMessage.getBody().getUserToken(),
                    agentMessage.getBody().getSourceHost(),
                    agentMessage.getBody().getSourcePort(),
                    agentMessage.getBody().getTargetHost(),
                    agentMessage.getBody().getTargetPort(),
                    ProxyMessageBodyType.TCP_DATA_FAIL,
                    agentMessage.getBody().getAgentChannelId(), null, null);
            var proxyMessage =
                    new ProxyMessage(UUIDUtil.INSTANCE.generateUuidInBytes(),
                            EncryptionType
                                    .choose(), proxyMessageBody);
            proxyChannel.writeAndFlush(proxyMessage)
                    .addListener((ChannelFutureListener) proxyChannelFuture -> {
                        if (proxyChannelFuture.isSuccess()) {
                            PpaassLogger.INSTANCE.debug(
                                    () -> "Success to write TCP_DATA_FAIL(1) result to agent, agent message:\n{}\n",
                                    () -> new Object[]{
                                            agentMessage,
                                            proxyChannelFuture.cause()
                                    });
                            return;
                        }
                        PpaassLogger.INSTANCE.error(
                                () -> "Fail to write TCP_DATA_FAIL(1) result to agent because of exception, agent message:\n{}\n",
                                () -> new Object[]{
                                        agentMessage,
                                        proxyChannelFuture.cause()
                                });
                        proxyChannel.close();
                    });
            return;
        }
        targetChannel.writeAndFlush(
                Unpooled.wrappedBuffer(agentMessage.getBody().getData()))
                .addListener((ChannelFutureListener) targetChannelFuture -> {
                    if (targetChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE.debug(
                                () -> "Success to write agent data to target, agent message:\n{}\n ",
                                () -> new Object[]{
                                        agentMessage,
                                        targetChannelFuture.cause()
                                });
                        return;
                    }
                    targetChannel.close();
                    PpaassLogger.INSTANCE.error(
                            () -> "Fail to write agent data to target because of exception, agent message:\n{}\n ",
                            () -> new Object[]{
                                    agentMessage,
                                    targetChannelFuture.cause()
                            });
                    var failProxyMessageBody = new ProxyMessageBody(UUIDUtil.INSTANCE.generateUuid(),
                            proxyConfiguration.getProxyInstanceId(),
                            agentMessage.getBody().getUserToken(),
                            agentMessage.getBody().getSourceHost(),
                            agentMessage.getBody().getSourcePort(),
                            agentMessage.getBody().getTargetHost(),
                            agentMessage.getBody().getTargetPort(),
                            ProxyMessageBodyType.TCP_DATA_FAIL,
                            agentMessage.getBody().getAgentChannelId(),
                            targetChannel.id().asLongText(),
                            null);
                    var failProxyMessage = new ProxyMessage(UUIDUtil.INSTANCE.generateUuidInBytes(),
                            EncryptionType.choose(), failProxyMessageBody);
                    proxyChannel.writeAndFlush(failProxyMessage)
                            .addListener((ChannelFutureListener) proxyChannelFuture -> {
                                if (proxyChannelFuture.isSuccess()) {
                                    PpaassLogger.INSTANCE.debug(
                                            () -> "Success to write TCP_DATA_FAIL(2) result to agent, agent message:\n{}\n",
                                            () -> new Object[]{
                                                    agentMessage,
                                                    proxyChannelFuture.cause()
                                            });
                                    return;
                                }
                                PpaassLogger.INSTANCE.error(
                                        () -> "Fail to write TCP_DATA_FAIL(2) result to agent because of exception, agent message:\n{}\n",
                                        () -> new Object[]{
                                                agentMessage,
                                                proxyChannelFuture.cause()
                                        });
                                proxyChannel.close();
                            });
                });
    }

    private void handleUdpData(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) {
        var proxyTcpChannel = proxyChannelContext.channel();
        var destinationInetSocketAddress =
                new InetSocketAddress(agentMessage.getBody().getTargetHost(),
                        agentMessage.getBody().getTargetPort());
        var udpPackage = new DatagramPacket(agentMessage.getBody().getData(), agentMessage.getBody().getData().length,
                destinationInetSocketAddress);
        PpaassLogger.INSTANCE.debug(
                () -> "Receive agent message for udp, agent message: \n{}\n",
                () -> new Object[]{
                        agentMessage
                });
        DatagramSocket targetUdpSocket = null;
        try {
            targetUdpSocket = new DatagramSocket();
        } catch (SocketException e) {
            var failProxyMessageBody = new ProxyMessageBody(UUIDUtil.INSTANCE.generateUuid(),
                    proxyConfiguration.getProxyInstanceId(),
                    agentMessage.getBody().getUserToken(),
                    agentMessage.getBody().getSourceHost(),
                    agentMessage.getBody().getSourcePort(),
                    agentMessage.getBody().getTargetHost(),
                    agentMessage.getBody().getTargetPort(),
                    ProxyMessageBodyType.UDP_DATA_FAIL,
                    agentMessage.getBody().getAgentChannelId(),
                    null,
                    null);
            var failProxyMessage = new ProxyMessage(UUIDUtil.INSTANCE.generateUuidInBytes(),
                    EncryptionType.choose(), failProxyMessageBody);
            proxyTcpChannel.writeAndFlush(failProxyMessage)
                    .addListener((ChannelFutureListener) proxyChannelFuture -> {
                        if (proxyChannelFuture.isSuccess()) {
                            PpaassLogger.INSTANCE.debug(
                                    () -> "Success to write UDP_DATA result to agent, agent message:\n{}\n",
                                    () -> new Object[]{
                                            agentMessage,
                                            proxyChannelFuture.cause()
                                    });
                            return;
                        }
                        PpaassLogger.INSTANCE.error(
                                () -> "Fail to write UDP_DATA result to agent because of exception, agent message:\n{}\n",
                                () -> new Object[]{
                                        agentMessage,
                                        proxyChannelFuture.cause()
                                });
                        proxyTcpChannel.close();
                    });
            return;
        }
        try {
            targetUdpSocket.setSoTimeout(this.proxyConfiguration.getTargetUdpReceiveTimeout());
            targetUdpSocket.send(udpPackage);
            var receiveDataPacketBuf = new byte[1024 * 64];
            DatagramPacket receiveDataPacket = new DatagramPacket(receiveDataPacketBuf, receiveDataPacketBuf.length);
            targetUdpSocket.receive(receiveDataPacket);
            var proxyMessageBody =
                    new ProxyMessageBody(
                            UUIDUtil.INSTANCE.generateUuid(),
                            proxyConfiguration.getProxyInstanceId(),
                            agentMessage.getBody().getUserToken(),
                            agentMessage.getBody().getSourceHost(),
                            agentMessage.getBody().getSourcePort(),
                            agentMessage.getBody().getTargetHost(),
                            agentMessage.getBody().getTargetPort(),
                            ProxyMessageBodyType.UDP_DATA_SUCCESS,
                            agentMessage.getBody().getAgentChannelId(),
                            null,
                            receiveDataPacket.getData());
            var proxyMessage =
                    new ProxyMessage(
                            UUIDUtil.INSTANCE.generateUuidInBytes(),
                            EncryptionType.choose(),
                            proxyMessageBody);
            proxyTcpChannel.writeAndFlush(proxyMessage).addListener(future -> {
                if (future.isSuccess()) {
                    return;
                }
                proxyTcpChannel.close();
            });
        } catch (IOException e) {
            var failProxyMessageBody = new ProxyMessageBody(UUIDUtil.INSTANCE.generateUuid(),
                    proxyConfiguration.getProxyInstanceId(),
                    agentMessage.getBody().getUserToken(),
                    agentMessage.getBody().getSourceHost(),
                    agentMessage.getBody().getSourcePort(),
                    agentMessage.getBody().getTargetHost(),
                    agentMessage.getBody().getTargetPort(),
                    ProxyMessageBodyType.UDP_DATA_FAIL,
                    agentMessage.getBody().getAgentChannelId(),
                    null,
                    null);
            var failProxyMessage = new ProxyMessage(UUIDUtil.INSTANCE.generateUuidInBytes(),
                    EncryptionType.choose(), failProxyMessageBody);
            proxyTcpChannel.writeAndFlush(failProxyMessage)
                    .addListener((ChannelFutureListener) proxyChannelFuture -> {
                        if (proxyChannelFuture.isSuccess()) {
                            PpaassLogger.INSTANCE.debug(
                                    () -> "Success to write UDP_DATA result to agent, agent message:\n{}\n",
                                    () -> new Object[]{
                                            agentMessage,
                                            proxyChannelFuture.cause()
                                    });
                            return;
                        }
                        PpaassLogger.INSTANCE.error(
                                () -> "Fail to write UDP_DATA result to agent because of exception, agent message:\n{}\n",
                                () -> new Object[]{
                                        agentMessage,
                                        proxyChannelFuture.cause()
                                });
                        proxyTcpChannel.close();
                    });
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) {
        var agentMessageBodyType = agentMessage.getBody().getBodyType();
        switch (agentMessageBodyType) {
            case TCP_DATA -> this.handleTcpData(proxyChannelContext, agentMessage);
            case UDP_DATA -> this.handleUdpData(proxyChannelContext, agentMessage);
            case TCP_CONNECT -> this.handleTcpConnect(proxyChannelContext, agentMessage);
        }
    }
}
