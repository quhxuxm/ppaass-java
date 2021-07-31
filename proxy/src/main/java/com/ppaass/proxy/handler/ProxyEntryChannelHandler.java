package com.ppaass.proxy.handler;

import com.ppaass.common.exception.PpaassException;
import com.ppaass.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.message.*;
import com.ppaass.proxy.IProxyConstant;
import com.ppaass.proxy.ProxyConfiguration;
import com.ppaass.proxy.handler.bo.TargetTcpInfo;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.*;
import java.util.Arrays;

import static com.ppaass.proxy.IProxyConstant.DNS_PORT;

@Service
@ChannelHandler.Sharable
public class ProxyEntryChannelHandler extends SimpleChannelInboundHandler<AgentMessage> {
    private final Logger logger = LoggerFactory.getLogger(ProxyEntryChannelHandler.class);
    private static final int UDP_PACKET_MAX_LENGTH = 65535;
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
        logger.error("Exception happen on proxy channel=[{}]", proxyChannel, cause);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext proxyChannelContext) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var targetChannel = proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNEL).get();
        if (targetChannel == null) {
            return;
        }
        if (targetChannel.isActive()) {
            targetChannel.close();
        }
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
        logger.debug("Begin to create TCP connection for: {}", agentMessage);
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
                                                "Success to write TCP connect result (TCP_CONNECT_FAIL) to agent, agent message:\n{}\n",
                                                agentMessage,
                                                proxyChannelFuture.cause()
                                        );
                                    } else {
                                        logger.error(
                                                "Fail to write TCP connect result (TCP_CONNECT_FAIL) to agent because of exception, agent message:\n{}\n",
                                                agentMessage,
                                                proxyChannelFuture.cause()
                                        );
                                    }
                                    proxyChannel.close();
                                });
                        logger
                                .error("Fail to create TCP connection for: {}", agentMessage);
                        return;
                    }
                    logger
                            .debug("Success to create TCP connection for: {}", agentMessage);
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
                            .debug("Create TCP connection info: \n{}\n",
                                    targetTcpConnectionInfo);
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
                                            "Success to write TCP connect result (TCP_CONNECT_SUCCESS) to agent, TCP connection info:\n{}\n",
                                            targetTcpConnectionInfo,
                                            proxyChannelFuture.cause()
                                    );
                                    return;
                                }
                                logger.error(
                                        "Fail to write TCP connect result (TCP_CONNECT_SUCCESS) to agent because of exception, TCP connection info:\n{}\n",
                                        targetTcpConnectionInfo,
                                        proxyChannelFuture.cause()
                                );
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
                                    "Success to write TCP_DATA_FAIL(1) result to agent, agent message:\n{}\n",
                                    agentMessage,
                                    proxyChannelFuture.cause()
                            );
                            return;
                        }
                        logger.error(
                                "Fail to write TCP_DATA_FAIL(1) result to agent because of exception, agent message:\n{}\n",
                                agentMessage,
                                proxyChannelFuture.cause()
                        );
                        proxyChannel.close();
                    });
            return;
        }
        targetChannel.writeAndFlush(
                        Unpooled.wrappedBuffer(agentMessage.getBody().getData()))
                .addListener((ChannelFutureListener) targetChannelFuture -> {
                    if (targetChannelFuture.isSuccess()) {
                        logger.trace(
                                "Success to write agent data to target, target channel={}, agent message:\n{}\n ",
                                targetChannel.id().asLongText(),
                                agentMessage
                        );
                        return;
                    }
                    targetChannel.close();
                    logger.trace("The target channel is broke, target channel={}", targetChannel);
                    var reconnectTargetChannel = this.targetTcpBootstrap
                            .connect(agentMessage.getBody().getTargetHost(),
                                    agentMessage.getBody().getTargetPort()).sync().channel();
                    reconnectTargetChannel.attr(IProxyConstant.ITargetChannelAttr.TCP_INFO)
                            .set(targetChannel.attr(IProxyConstant.ITargetChannelAttr.TCP_INFO).get());
                    proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNEL).set(reconnectTargetChannel);
                    reconnectTargetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.getBody().getData()))
                            .addListener((ChannelFutureListener) future -> {
                                if (!future.isSuccess()) {
                                    logger.trace(
                                            "Success to write agent data to target, target channel={}, agent message:\n{}\n ",
                                            reconnectTargetChannel.id().asLongText(),
                                            agentMessage
                                    );
                                    logger.error(
                                            "Fail to write agent data to target because of exception, agent message:\n{}\n ",
                                            agentMessage,
                                            future.cause()
                                    );
                                    var failProxyMessageBody = new ProxyMessageBody(UUIDUtil.INSTANCE.generateUuid(),
                                            proxyConfiguration.getProxyInstanceId(),
                                            agentMessage.getBody().getUserToken(),
                                            agentMessage.getBody().getSourceHost(),
                                            agentMessage.getBody().getSourcePort(),
                                            agentMessage.getBody().getTargetHost(),
                                            agentMessage.getBody().getTargetPort(),
                                            ProxyMessageBodyType.TCP_DATA_FAIL,
                                            agentMessage.getBody().getAgentChannelId(),
                                            reconnectTargetChannel.id().asLongText(),
                                            null);
                                    var failProxyMessage = new ProxyMessage(UUIDUtil.INSTANCE.generateUuidInBytes(),
                                            EncryptionType.choose(), failProxyMessageBody);
                                    proxyChannel.writeAndFlush(failProxyMessage)
                                            .addListener((ChannelFutureListener) proxyChannelFuture -> {
                                                if (proxyChannelFuture.isSuccess()) {
                                                    logger.debug(
                                                            "Success to write TCP_DATA_FAIL(2) result to agent, agent message:\n{}\n",
                                                            agentMessage,
                                                            proxyChannelFuture.cause()
                                                    );
                                                    return;
                                                }
                                                logger.error(
                                                        "Fail to write TCP_DATA_FAIL(2) result to agent because of exception, agent message:\n{}\n",
                                                        agentMessage,
                                                        proxyChannelFuture.cause()
                                                );
                                                proxyChannel.close();
                                            });
                                }
                            });
                });
    }

    private void handleDnsQuery(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) {
        var proxyTcpChannel = proxyChannelContext.channel();
        var dnsQueryData = agentMessage.getBody().getData();
        InetSocketAddress originalDestinationSocketAddress =
                new InetSocketAddress(agentMessage.getBody().getTargetHost(), DNS_PORT);
        InetSocketAddress originalSenderSocketAddress =
                new InetSocketAddress(agentMessage.getBody().getSourceHost(),
                        agentMessage.getBody().getSourcePort());
        io.netty.channel.socket.DatagramPacket datagramPacket =
                new io.netty.channel.socket.DatagramPacket(Unpooled.wrappedBuffer(dnsQueryData),
                        originalDestinationSocketAddress);
        EmbeddedChannel dnsChannel =
                new EmbeddedChannel(new DatagramDnsQueryDecoder(), new DatagramDnsResponseEncoder());
        dnsChannel.writeInbound(datagramPacket);
        DatagramDnsQuery dnsQuery = dnsChannel.readInbound();
        DefaultDnsQuestion dnsQuestion = dnsQuery.recordAt(DnsSection.QUESTION);
        logger.debug("DNS question,id=[{}],  name=[{}], question class=[{}], question type=[{}], ttl=[{}]",
                dnsQuery.id(),
                dnsQuestion.name(),
                dnsQuestion.dnsClass(),
                dnsQuestion.type().name(),
                dnsQuestion.timeToLive()
        );
        InetAddress[] allIpAddresses;
        try {
            allIpAddresses = InetAddress.getAllByName(dnsQuestion.name());
        } catch (UnknownHostException e) {
            logger.error("Fail to get all ip address of the given domain name [{}] because of exception.",
                    dnsQuestion.name(),
                    e
            );
            DatagramDnsResponse dnsResponse =
                    new DatagramDnsResponse(originalDestinationSocketAddress, originalSenderSocketAddress,
                            dnsQuery.id(), DnsOpCode.QUERY, DnsResponseCode.NXDOMAIN);
            DefaultDnsRawRecord dnsAnswer =
                    new DefaultDnsRawRecord(dnsQuestion.name(), DnsRecordType.SOA, 60 * 1000,
                            Unpooled.EMPTY_BUFFER);
            dnsResponse.addRecord(DnsSection.QUESTION, dnsQuestion);
            dnsResponse.addRecord(DnsSection.AUTHORITY, dnsAnswer);
            dnsChannel.writeOutbound(dnsResponse);
            io.netty.channel.socket.DatagramPacket dnsResponseUdpPacket = dnsChannel.flushOutbound().readOutbound();
            var dnsUdpResponsePacketContentByteBuf = dnsResponseUdpPacket.content();
            var dnsUdpResponsePacketContentByteArray = ByteBufUtil.getBytes(dnsUdpResponsePacketContentByteBuf);
            logger.debug(
                    "DNS answer,id=[{}],  name=[{}], question class=[{}], question type=[{}], ttl=[{}], question sender=[{}], question recipient=[{}]," +
                            "answer sender=[{}], answer recipient=[{}], ip=[NONE1]",
                    dnsQuery.id(),
                    dnsQuestion.name(),
                    dnsQuestion.dnsClass(),
                    dnsQuestion.type().name(),
                    dnsAnswer.timeToLive(),
                    dnsQuery.sender(),
                    dnsQuery.recipient(),
                    dnsResponse.sender(),
                    dnsResponse.recipient()
            );
            this.sendUdpDataToAgent(agentMessage, proxyTcpChannel, dnsUdpResponsePacketContentByteArray,
                    ProxyMessageBodyType.DNS_ANSWER);
            return;
        }
        if (allIpAddresses.length == 0) {
            logger.error("Fail to get all ip address of the given domain name [{}] because no ip address return",
                    dnsQuestion.name()
            );
            DatagramDnsResponse dnsResponse =
                    new DatagramDnsResponse(originalDestinationSocketAddress, originalSenderSocketAddress,
                            dnsQuery.id(), DnsOpCode.QUERY, DnsResponseCode.NXDOMAIN);
            DefaultDnsRawRecord dnsAnswer =
                    new DefaultDnsRawRecord(dnsQuestion.name(), DnsRecordType.SOA, 60 * 1000,
                            Unpooled.EMPTY_BUFFER);
            dnsResponse.addRecord(DnsSection.QUESTION, dnsQuestion);
            dnsResponse.addRecord(DnsSection.AUTHORITY, dnsAnswer);
            dnsChannel.writeOutbound(dnsResponse);
            io.netty.channel.socket.DatagramPacket dnsResponseUdpPacket = dnsChannel.flushOutbound().readOutbound();
            var dnsUdpResponsePacketContentByteBuf = dnsResponseUdpPacket.content();
            var dnsUdpResponsePacketContentByteArray = ByteBufUtil.getBytes(dnsUdpResponsePacketContentByteBuf);
            logger.debug(
                    "DNS answer,id=[{}],  name=[{}], question class=[{}], question type=[{}], ttl=[{}], question sender=[{}], question recipient=[{}]," +
                            "answer sender=[{}], answer recipient=[{}], ip=[NONE2]",
                    dnsQuery.id(),
                    dnsQuestion.name(),
                    dnsQuestion.dnsClass(),
                    dnsQuestion.type().name(),
                    dnsAnswer.timeToLive(),
                    dnsQuery.sender(),
                    dnsQuery.recipient(),
                    dnsResponse.sender(),
                    dnsResponse.recipient()
            );
            this.sendUdpDataToAgent(agentMessage, proxyTcpChannel, dnsUdpResponsePacketContentByteArray,
                    ProxyMessageBodyType.DNS_ANSWER);
            return;
        }
        logger.debug(
                "DNS get ip address,id=[{}],  name=[{}], question class=[{}], question type=[{}], question sender=[{}], question recipient=[{}], ip=[{}]",
                dnsQuery.id(),
                dnsQuestion.name(),
                dnsQuestion.dnsClass(),
                dnsQuestion.type().name(),
                dnsQuery.sender(),
                dnsQuery.recipient(),
                allIpAddresses[0].toString()
        );
        DatagramDnsResponse dnsResponse =
                new DatagramDnsResponse(originalDestinationSocketAddress, originalSenderSocketAddress,
                        dnsQuery.id());
        DefaultDnsRawRecord dnsAnswer = new DefaultDnsRawRecord(dnsQuestion.name(), DnsRecordType.A, 60 * 1000,
                Unpooled.wrappedBuffer(allIpAddresses[0].getAddress()));
        dnsResponse.addRecord(DnsSection.QUESTION, dnsQuestion);
        dnsResponse.addRecord(DnsSection.ANSWER, dnsAnswer);
        dnsChannel.writeOutbound(dnsResponse);
        io.netty.channel.socket.DatagramPacket dnsResponseUdpPacket = dnsChannel.flushOutbound().readOutbound();
        var dnsUdpResponsePacketContentByteBuf = dnsResponseUdpPacket.content();
        var dnsUdpResponsePacketContentByteArray = ByteBufUtil.getBytes(dnsUdpResponsePacketContentByteBuf);
        logger.debug(
                "DNS answer,id=[{}],  name=[{}], question class=[{}], question type=[{}], ttl=[{}], question sender=[{}], question recipient=[{}]," +
                        "answer sender=[{}], answer recipient=[{}], ip=[{}]",
                dnsQuery.id(),
                dnsQuestion.name(),
                dnsQuestion.dnsClass(),
                dnsQuestion.type().name(),
                dnsAnswer.timeToLive(),
                dnsQuery.sender(),
                dnsQuery.recipient(),
                dnsResponse.sender(),
                dnsResponse.recipient(),
                allIpAddresses[0].toString()
        );
        this.sendUdpDataToAgent(agentMessage, proxyTcpChannel, dnsUdpResponsePacketContentByteArray,
                ProxyMessageBodyType.DNS_ANSWER);
    }

    private void handleUdpData(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) {
        var proxyTcpChannel = proxyChannelContext.channel();
        var destinationInetSocketAddress =
                new InetSocketAddress(agentMessage.getBody().getTargetHost(),
                        agentMessage.getBody().getTargetPort());
        int udpPacketLength = agentMessage.getBody().getData().length;
        if (udpPacketLength > UDP_PACKET_MAX_LENGTH) {
            udpPacketLength = UDP_PACKET_MAX_LENGTH;
        }
        var udpPackage = new DatagramPacket(agentMessage.getBody().getData(), udpPacketLength,
                destinationInetSocketAddress);
        logger.debug(
                "Receive agent message for udp, agent message: \n{}\nudp data: \n{}\n",
                agentMessage,
                ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(agentMessage.getBody().getData()))
        );
        DatagramSocket targetUdpSocket = null;
        try {
            targetUdpSocket = new DatagramSocket();
        } catch (SocketException e) {
            logger.error("Fail to create UDP socket for target, agent message:\n{}\n",
                    agentMessage, e
            );
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
                                    "Success to write UDP_DATA result to agent, agent message:\n{}\n",
                                    agentMessage,
                                    proxyChannelFuture.cause()
                            );
                            return;
                        }
                        logger.error(
                                "Fail to write UDP_DATA result to agent because of exception, agent message:\n{}\n",
                                agentMessage,
                                proxyChannelFuture.cause()
                        );
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
            logger.debug("Receive UDP packet:\n{}\n",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(proxyMessageData))
            );
            sendUdpDataToAgent(agentMessage, proxyTcpChannel, proxyMessageData,
                    ProxyMessageBodyType.UDP_DATA_SUCCESS);
//            while (currentReceivedDataLength >= UDP_PACKET_MAX_LENGTH) {
//                DatagramPacket nextReceiveDataPacket = new DatagramPacket(receiveDataPacketBuf, UDP_PACKET_MAX_LENGTH);
//                try {
//                    targetUdpSocket.receive(nextReceiveDataPacket);
//                } catch (Exception e) {
//                    logger.debug(() -> "No more data from target UDP socket, agent message:\n{}\n",
//                            () -> new Object[]{
//                                    agentMessage, e
//                            });
//                    break;
//                }
//                currentReceivedDataLength = nextReceiveDataPacket.getLength();
//                byte[] nextProxyMessageData = Arrays.copyOf(nextReceiveDataPacket.getData(), currentReceivedDataLength);
//                sendUdpDataToAgent(agentMessage, proxyTcpChannel, nextProxyMessageData);
//            }
        } catch (Exception e) {
            logger.error("Fail to send UDP message to target UDP socket, agent message:\n{}\n",
                    agentMessage, e
            );
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
                                    "Success to write UDP_DATA result to agent, agent message:\n{}\n",
                                    agentMessage,
                                    proxyChannelFuture.cause()
                            );
                            return;
                        }
                        logger.error(
                                "Fail to write UDP_DATA result to agent because of exception, agent message:\n{}\n",
                                agentMessage,
                                proxyChannelFuture.cause()
                        );
                    });
        } finally {
            targetUdpSocket.close();
        }
    }

    private void sendUdpDataToAgent(AgentMessage agentMessage, Channel proxyTcpChannel,
                                    byte[] proxyMessageData, ProxyMessageBodyType proxyMessageBodyType) {
        var proxyMessageBody =
                new ProxyMessageBody(
                        UUIDUtil.INSTANCE.generateUuid(),
                        proxyConfiguration.getProxyInstanceId(),
                        agentMessage.getBody().getUserToken(),
                        agentMessage.getBody().getSourceHost(),
                        agentMessage.getBody().getSourcePort(),
                        agentMessage.getBody().getTargetHost(),
                        agentMessage.getBody().getTargetPort(),
                        proxyMessageBodyType,
                        agentMessage.getBody().getAgentChannelId(),
                        null,
                        proxyMessageData);
        logger.debug("Send UDP packet to agent, packet type = [{}], package content:\n{}\n", proxyMessageBodyType,
                ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(proxyMessageData)));
        var proxyMessage =
                new ProxyMessage(
                        UUIDUtil.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        proxyMessageBody);
        proxyTcpChannel.writeAndFlush(proxyMessage).addListener(future -> {
            if (future.isSuccess()) {
                return;
            }
            logger
                    .error("Fail to send udp data to agent because of exception happen when write data to agent, proxy channel = {}.",
                            proxyTcpChannel.id().asLongText(),
                            future.cause());
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
            case DNS_QUERY -> this.handleDnsQuery(proxyChannelContext, agentMessage);
            case TCP_CONNECT -> this.handleTcpConnect(proxyChannelContext, agentMessage);
        }
    }
}
