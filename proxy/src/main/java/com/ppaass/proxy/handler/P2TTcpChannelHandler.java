package com.ppaass.proxy.handler;

import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.message.*;
import com.ppaass.proxy.IProxyConstant;
import com.ppaass.proxy.handler.bo.TcpConnectionInfo;
import com.ppaass.proxy.handler.bo.UdpConnectionInfo;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

@Service
@ChannelHandler.Sharable
public class P2TTcpChannelHandler extends SimpleChannelInboundHandler<AgentMessage> {
    private static final Logger logger = LoggerFactory.getLogger(P2TTcpChannelHandler.class);
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
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, AgentMessage agentMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentMessageBodyType = agentMessage.getBody().getBodyType();
        switch (agentMessageBodyType) {
            case TCP_DATA -> {
                var agentTcpConnectionInfo =
                        proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
                if (agentTcpConnectionInfo == null) {
                    logger.error(
                            "Fail to transfer data from proxy to target because of no agent connection information attached, proxy channel = {}.",
                            proxyChannel.id().asLongText());
                    proxyChannel.close();
                    return;
                }
                var targetTcpChannel = agentTcpConnectionInfo.getTargetTcpChannel();
                targetTcpChannel.writeAndFlush(
                        Unpooled.wrappedBuffer(agentMessage.getBody().getData()))
                        .addListener((ChannelFutureListener) targetChannelFuture -> {
                            if (targetChannelFuture.isSuccess()) {
                                proxyChannel.read();
                                //targetTcpChannel.read();
                                return;
                            }
                            logger.error(
                                    "Fail to write agent message to target because of exception, proxy channel = {}, target channel = {}",
                                    proxyChannel.id().asLongText(), targetTcpChannel.id().asLongText(),
                                    targetChannelFuture.cause());
                            targetTcpChannel.close();
                            proxyChannel.close();
                        });
            }
            case UDP_DATA -> {
                var recipient = new InetSocketAddress(agentMessage.getBody().getTargetHost(),
                        agentMessage.getBody().getTargetPort());
                var udpData = Unpooled.wrappedBuffer(agentMessage.getBody().getData());
                var udpPackage = new DatagramPacket(udpData, recipient);
                logger.debug("Agent message for udp: {}, data: \n{}\n", agentMessage,
                        ByteBufUtil.prettyHexDump(udpData));
                var udpConnectionInfo = proxyChannel.attr(IProxyConstant.UDP_CONNECTION_INFO).get();
                if (udpConnectionInfo == null) {
                    var targetUdpChannel =
                            targetUdpBootstrap.bind(0).sync().channel();
                    udpConnectionInfo = new UdpConnectionInfo(
                            agentMessage.getBody().getTargetHost(),
                            agentMessage.getBody().getTargetPort(),
                            agentMessage.getBody().getUserToken(),
                            proxyChannel,
                            targetUdpChannel
                    );
                    targetUdpChannel.attr(IProxyConstant.UDP_CONNECTION_INFO).setIfAbsent(udpConnectionInfo);
                }
                logger.debug("Receive udp package from agent: {}", udpPackage);
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
                                        MessageSerializer.INSTANCE.generateUuid(),
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
                            var agentConnectionInfo = new TcpConnectionInfo(
                                    agentMessage.getBody().getTargetHost(),
                                    agentMessage.getBody().getTargetPort(),
                                    agentMessage.getBody().getUserToken(),
                                    proxyChannel,
                                    targetChannel,
                                    agentMessage.getBody().getBodyType() ==
                                            AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE
                            );
                            targetChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).setIfAbsent(agentConnectionInfo);
                            proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).setIfAbsent(agentConnectionInfo);
                            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE,
                                    agentConnectionInfo.isTargetTcpConnectionKeepAlive());
                            targetChannel.config()
                                    .setOption(ChannelOption.SO_KEEPALIVE,
                                            agentConnectionInfo.isTargetTcpConnectionKeepAlive());
                            var proxyMessageBody =
                                    new ProxyMessageBody(
                                            MessageSerializer.INSTANCE.generateUuid(),
                                            agentConnectionInfo.getUserToken(),
                                            agentConnectionInfo.getTargetHost(),
                                            agentConnectionInfo.getTargetPort(),
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
                                        logger.error(
                                                "Fail to write connect result message to agent because of exception, proxy channel = {}, target channel = {}",
                                                proxyChannel.id().asLongText(), targetChannel.id().asLongText(),
                                                proxyChannelFuture.cause());
                                        targetChannel.close();
                                        proxyChannel.close();
                                    });
                        });
            }
            default -> {
                logger.error(
                        "Fail to transfer data from proxy to target because of it is not a validate body type, proxy channel = {}.",
                        proxyChannel.id().asLongText());
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
