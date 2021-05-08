package com.ppaass.proxy.handler;

import com.ppaass.common.exception.PpaassException;
import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.message.*;
import com.ppaass.proxy.IProxyConstant;
import com.ppaass.proxy.ProxyConfiguration;
import com.ppaass.proxy.handler.bo.TargetTcpInfo;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;

@Service
@ChannelHandler.Sharable
public class ProxyEntryChannelHandler extends SimpleChannelInboundHandler<AgentMessage> {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private static final int UDP_PACKET_MAX_LENGTH = 64 * 1024;
    private final Bootstrap targetTcpBootstrap;
    private final ProxyConfiguration proxyConfiguration;

    public ProxyEntryChannelHandler(Bootstrap targetTcpBootstrap,
                                    ProxyConfiguration proxyConfiguration) {
        this.targetTcpBootstrap = targetTcpBootstrap;
        this.proxyConfiguration = proxyConfiguration;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext proxyChannelContext, Throwable cause) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        proxyChannel.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext proxyChannelContext) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var targetChannel = proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNEL).get();
        if (targetChannel == null) {
            return;
        }
        targetChannel.config().setAutoRead(proxyChannel.isWritable());
    }

    private void handleTcpConnect(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) {
        var proxyChannel = proxyChannelContext.channel();
        logger.debug(() -> "Begin to create TCP connection for: {}", () -> new Object[]{agentMessage});
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
                                        logger.debug(
                                                () -> "Success to write TCP connect result (TCP_CONNECT_FAIL) to agent, agent message:\n{}\n",
                                                () -> new Object[]{
                                                        agentMessage,
                                                        proxyChannelFuture.cause()
                                                });
                                    } else {
                                        logger.error(
                                                () -> "Fail to write TCP connect result (TCP_CONNECT_FAIL) to agent because of exception, agent message:\n{}\n",
                                                () -> new Object[]{
                                                        agentMessage,
                                                        proxyChannelFuture.cause()
                                                });
                                    }
                                    proxyChannel.close();
                                });
                        logger
                                .error(() -> "Fail to create TCP connection for: {}", () -> new Object[]{agentMessage});
                        return;
                    }
                    logger
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
                    logger
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
                                    logger.info(
                                            () -> "Success to write TCP connect result (TCP_CONNECT_SUCCESS) to agent, TCP connection info:\n{}\n",
                                            () -> new Object[]{
                                                    targetTcpConnectionInfo,
                                                    proxyChannelFuture.cause()
                                            });
                                    return;
                                }
                                logger.error(
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
                            logger.debug(
                                    () -> "Success to write TCP_DATA_FAIL(1) result to agent, agent message:\n{}\n",
                                    () -> new Object[]{
                                            agentMessage,
                                            proxyChannelFuture.cause()
                                    });
                            return;
                        }
                        logger.error(
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
                        logger.debug(
                                () -> "Success to write agent data to target, agent message:\n{}\n ",
                                () -> new Object[]{
                                        agentMessage,
                                        targetChannelFuture.cause()
                                });
                        return;
                    }
                    targetChannel.close();
                    logger.error(
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
                                    logger.debug(
                                            () -> "Success to write TCP_DATA_FAIL(2) result to agent, agent message:\n{}\n",
                                            () -> new Object[]{
                                                    agentMessage,
                                                    proxyChannelFuture.cause()
                                            });
                                    return;
                                }
                                logger.error(
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
        logger.debug(
                () -> "Receive agent message for udp, agent message: \n{}\n",
                () -> new Object[]{
                        agentMessage
                });
        DatagramSocket targetUdpSocket = null;
        try {
            targetUdpSocket = new DatagramSocket();
        } catch (SocketException e) {
            logger.error(() -> "Fail to create UDP socket for target, agent message:\n{}\n", () -> new Object[]{
                    agentMessage, e
            });
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
                            logger.debug(
                                    () -> "Success to write UDP_DATA result to agent, agent message:\n{}\n",
                                    () -> new Object[]{
                                            agentMessage,
                                            proxyChannelFuture.cause()
                                    });
                            return;
                        }
                        logger.error(
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
            var receiveDataPacketBuf = new byte[UDP_PACKET_MAX_LENGTH];
            DatagramPacket receiveDataPacket = new DatagramPacket(receiveDataPacketBuf, UDP_PACKET_MAX_LENGTH);
            targetUdpSocket.receive(receiveDataPacket);
            int currentReceivedDataLength = receiveDataPacket.getLength();
            byte[] proxyMessageData = Arrays.copyOf(receiveDataPacket.getData(), currentReceivedDataLength);
            sendUdpDataToAgent(agentMessage, proxyTcpChannel, proxyMessageData);
            while (currentReceivedDataLength >= UDP_PACKET_MAX_LENGTH) {
                DatagramPacket nextReceiveDataPacket = new DatagramPacket(receiveDataPacketBuf, UDP_PACKET_MAX_LENGTH);
                try {
                    targetUdpSocket.receive(nextReceiveDataPacket);
                } catch (Exception e) {
                    logger.debug(() -> "No more data from target UDP socket, agent message:\n{}\n",
                            () -> new Object[]{
                                    agentMessage, e
                            });
                    break;
                }
                currentReceivedDataLength = nextReceiveDataPacket.getLength();
                byte[] nextProxyMessageData = Arrays.copyOf(nextReceiveDataPacket.getData(), currentReceivedDataLength);
                sendUdpDataToAgent(agentMessage, proxyTcpChannel, nextProxyMessageData);
            }
        } catch (Exception e) {
            logger.error(() -> "Fail to send UDP message to target UDP socket, agent message:\n{}\n",
                    () -> new Object[]{
                            agentMessage, e
                    });
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
                            logger.debug(
                                    () -> "Success to write UDP_DATA result to agent, agent message:\n{}\n",
                                    () -> new Object[]{
                                            agentMessage,
                                            proxyChannelFuture.cause()
                                    });
                            return;
                        }
                        logger.error(
                                () -> "Fail to write UDP_DATA result to agent because of exception, agent message:\n{}\n",
                                () -> new Object[]{
                                        agentMessage,
                                        proxyChannelFuture.cause()
                                });
                        proxyTcpChannel.close();
                    });
        } finally {
            targetUdpSocket.close();
        }
    }

    private void sendUdpDataToAgent(AgentMessage agentMessage, Channel proxyTcpChannel,
                                    byte[] proxyMessageData) {
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
                        proxyMessageData);
        var proxyMessage =
                new ProxyMessage(
                        UUIDUtil.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        proxyMessageBody);
        proxyTcpChannel.writeAndFlush(proxyMessage).syncUninterruptibly().addListener(future -> {
            if (future.isSuccess()) {
                return;
            }
            proxyTcpChannel.close();
            logger
                    .error(() -> "Fail to send udp data to agent because of exception happen when write data to agent, proxy channel = {}.",
                            () -> new Object[]{
                                    proxyTcpChannel.id().asLongText(),
                                    future.cause()});
            throw new PpaassException(
                    "Fail to send udp data to agent because of exception happen when write data to agent.",
                    future.cause());
        });
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
