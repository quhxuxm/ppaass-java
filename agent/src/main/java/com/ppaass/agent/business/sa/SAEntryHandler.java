package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.message.AgentMessage;
import com.ppaass.protocol.vpn.message.AgentMessageBody;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import com.ppaass.protocol.vpn.message.EncryptionType;
import io.netty.channel.*;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class SAEntryHandler extends SimpleChannelInboundHandler<SocksMessage> {
    private final AgentConfiguration agentConfiguration;
    private final SAProxyResourceManager saProxyResourceManager;

    public SAEntryHandler(SAProxyResourceManager saProxyResourceManager,
                          AgentConfiguration agentConfiguration) {
        this.saProxyResourceManager = saProxyResourceManager;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentChannelContext, SocksMessage socksRequest) throws Exception {
        var agentChannel = agentChannelContext.channel();
        if (SocksVersion.UNKNOWN == socksRequest.version()) {
            PpaassLogger.INSTANCE.error(
                    () -> "Incoming protocol is unknown protocol, agent channel = {}.", () -> new Object[]{
                            agentChannel.id().asLongText()
                    });
            agentChannel.close();
            return;
        }
        if (SocksVersion.SOCKS4a == socksRequest.version()) {
            PpaassLogger.INSTANCE
                    .error(() -> "Socks4a not support, agent channel = {}.",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            agentChannel.close();
            return;
        }
        var agentChannelPipeline = agentChannelContext.pipeline();
        if (socksRequest instanceof Socks5InitialRequest) {
            PpaassLogger.INSTANCE
                    .debug(
                            () -> "Socks5 initial request coming always NO_AUTH, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            agentChannelPipeline.addFirst(new Socks5CommandRequestDecoder());
            agentChannelContext.writeAndFlush(
                    new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                    .addListener((ChannelFutureListener) agentChannelFuture -> {
                        if (!agentChannelFuture.isSuccess()) {
                            agentChannel.close();
                            return;
                        }
                        agentChannelContext.read();
                    });
            return;
        }
        if (!(socksRequest instanceof Socks5CommandRequest)) {
            PpaassLogger.INSTANCE
                    .error(
                            () -> "Wrong socks5 request, agent channel = {} ",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            agentChannel.close();
            return;
        }
        var socks5CommandRequest = (Socks5CommandRequest) socksRequest;
        if (socks5CommandRequest.type() == Socks5CommandType.CONNECT) {
            PpaassLogger.INSTANCE
                    .debug(
                            () -> "Socks5 connect request coming, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            try {
                Channel proxyChannel = this.saProxyResourceManager.getProxyTcpChannelPool().borrowObject();
                this.processProxyConnect(agentChannel, proxyChannel, socks5CommandRequest);
            } catch (Exception e) {
                PpaassLogger.INSTANCE
                        .error(() -> "Fail to borrow proxy tcp channel connection from pool because of exception.",
                                () -> new Object[]{e});
                agentChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                        socks5CommandRequest.dstAddrType())).addListener(ChannelFutureListener.CLOSE);
            }
            return;
        }
        if (socks5CommandRequest.type() == Socks5CommandType.UDP_ASSOCIATE) {
            PpaassLogger.INSTANCE
                    .debug(
                            () -> "Socks5 udp associate request coming, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
            this.saProxyResourceManager.getProxyUdpChannelBootstrap().bind(0)
                    .addListener(new SAUdpBindListener(agentChannel,
                            this.saProxyResourceManager.getProxyTcpChannelPool().borrowObject(),
                            agentConfiguration, socks5CommandRequest));
            return;
        }
        PpaassLogger.INSTANCE
                .error(
                        () -> "Wrong socks5 request, agent channel = {} ",
                        () -> new Object[]{
                                agentChannel.id().asLongText()
                        });
        agentChannel.close();
    }

    private void processProxyConnect(Channel agentChannel, Channel proxyChannel,
                                     Socks5CommandRequest socks5CommandRequest) {
        PpaassLogger.INSTANCE.debug(
                () -> "Success connect to proxy, agent channel = {}",
                () -> new Object[]{agentChannel.id().asLongText()});
        var tcpConnectionInfo = new SATcpConnectionInfo(
                socks5CommandRequest.dstAddr(),
                socks5CommandRequest.dstPort(),
                socks5CommandRequest.dstAddrType(),
                agentConfiguration.getUserToken(),
                agentChannel,
                proxyChannel);
        agentChannel.attr(ISAConstant.IAgentChannelConstant.TCP_CONNECTION_INFO)
                .set(tcpConnectionInfo);
        var agentMessageBody = new AgentMessageBody(
                UUIDUtil.INSTANCE.generateUuid(),
                agentConfiguration.getAgentInstanceId(),
                agentConfiguration.getUserToken(),
                this.agentConfiguration.getAgentSourceAddress(), this.agentConfiguration.getTcpPort(),
                tcpConnectionInfo.getTargetHost(),
                tcpConnectionInfo.getTargetPort(),
                AgentMessageBodyType.TCP_CONNECT,
                agentChannel.id().asLongText(),
                null, null);
        var agentMessage = new AgentMessage(
                UUIDUtil.INSTANCE.generateUuidInBytes(),
                EncryptionType.choose(),
                agentMessageBody);
        var agentChannelPipeline = agentChannel.pipeline();
        try {
            agentChannelPipeline.remove(SAEntryHandler.class.getName());
        } catch (Exception e) {
            PpaassLogger.INSTANCE.debug(
                    () -> "Fail to remove SocksV5Handler from proxy channel pipeline, proxy channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText()});
        }
        PpaassLogger.INSTANCE.trace(
                () -> "Send TCP_CONNECT from agent to proxy [BEGIN] , agent channel = {}, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText()});
        proxyChannel.attr(ISAConstant.IProxyChannelConstant.AGENT_CHANNEL).set(agentChannel);
        proxyChannel.writeAndFlush(agentMessage)
                .addListener((ChannelFutureListener) proxyWriteChannelFuture -> {
                    if (proxyWriteChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE.trace(
                                () -> "Send TCP_CONNECT from agent to proxy [SUCCESS], agent channel = {}, proxy channel = {}",
                                () -> new Object[]{agentChannel.id().asLongText(), proxyChannel.id().asLongText()});
                        return;
                    }
                    PpaassLogger.INSTANCE.debug(
                            () -> "Send TCP_CONNECT from agent to proxy [FAIL], agent channel = {}, proxy channel = {}",
                            () -> new Object[]{agentChannel.id().asLongText(), proxyChannel.id().asLongText()});
                    agentChannel.writeAndFlush(
                            new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                                    tcpConnectionInfo.getTargetAddressType()))
                            .addListener(ChannelFutureListener.CLOSE);
                });
    }
}