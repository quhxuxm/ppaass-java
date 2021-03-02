package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.handler.socks.bo.SocksAgentTcpConnectionInfo;
import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.AgentMessage;
import com.ppaass.common.message.AgentMessageBody;
import com.ppaass.common.message.AgentMessageBodyType;
import com.ppaass.common.message.MessageSerializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

public class SocksAgentA2PConnectListener implements ChannelFutureListener {
    static {
        PpaassLogger.INSTANCE.register(SocksAgentA2PConnectListener.class);
    }

    private final Channel agentChannel;
    private final Socks5CommandRequest socks5CommandRequest;
    private final AgentConfiguration agentConfiguration;

    public SocksAgentA2PConnectListener(Channel agentChannel,
                                        Socks5CommandRequest socks5CommandRequest,
                                        AgentConfiguration agentConfiguration) {
        this.agentChannel = agentChannel;
        this.socks5CommandRequest = socks5CommandRequest;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void operationComplete(ChannelFuture proxyChannelFuture) throws Exception {
        if (!proxyChannelFuture.isSuccess()) {
            PpaassLogger.INSTANCE.error(SocksAgentA2PConnectListener.class,
                    () -> "Fail connect to proxy, agent channel = {}",
                    () -> new Object[]{agentChannel.id().asLongText()});
            agentChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                    socks5CommandRequest.dstAddrType()))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        PpaassLogger.INSTANCE.debug(SocksAgentA2PConnectListener.class,
                () -> "Success connect to proxy, agent channel = {}",
                () -> new Object[]{agentChannel.id().asLongText()});
        var proxyChannel = proxyChannelFuture.channel();
        var tcpConnectionInfo = new SocksAgentTcpConnectionInfo(
                socks5CommandRequest.dstAddr(),
                socks5CommandRequest.dstPort(),
                socks5CommandRequest.dstAddrType(),
                agentConfiguration.getUserToken(),
                true,
                agentChannel,
                proxyChannel);
        proxyChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO)
                .setIfAbsent(tcpConnectionInfo);
        agentChannel.attr(ISocksAgentConst.SOCKS_TCP_CONNECTION_INFO)
                .setIfAbsent(tcpConnectionInfo);
        var agentMessageBody = new AgentMessageBody(
                MessageSerializer.INSTANCE.generateUuid(),
                agentConfiguration.getUserToken(),
                tcpConnectionInfo.getTargetHost(),
                tcpConnectionInfo.getTargetPort(),
                AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE,
                new byte[]{});
        var agentMessage = new AgentMessage(
                MessageSerializer.INSTANCE.generateUuidInBytes(),
                EncryptionType.choose(),
                agentMessageBody);
        var agentChannelPipeline = this.agentChannel.pipeline();
        try {
            agentChannelPipeline.remove(SocksAgentProtocolHandler.class.getName());
        } catch (Exception e) {
            PpaassLogger.INSTANCE.debug(SocksAgentA2PConnectListener.class,
                    () -> "Fail to remove SocksV5Handler from proxy channel pipeline, proxy channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText()});
        }
        PpaassLogger.INSTANCE.trace(SocksAgentA2PConnectListener.class,
                () -> "Send CONNECT_WITH_KEEP_ALIVE from agent to proxy [BEGIN] , agent channel = {}, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText()});
        proxyChannel.writeAndFlush(agentMessage)
                .addListener((ChannelFutureListener) proxyWriteChannelFuture -> {
                    if (proxyWriteChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE.trace(SocksAgentA2PConnectListener.class,
                                () -> "Send CONNECT_WITH_KEEP_ALIVE from agent to proxy [SUCCESS], agent channel = {}, proxy channel = {}",
                                () -> new Object[]{agentChannel.id().asLongText(), proxyChannel.id().asLongText()});
                        proxyChannel.read();
                        return;
                    }
                    PpaassLogger.INSTANCE.debug(SocksAgentA2PConnectListener.class,
                            () -> "Send CONNECT_WITH_KEEP_ALIVE from agent to proxy [FAIL], agent channel = {}, proxy channel = {}",
                            () -> new Object[]{agentChannel.id().asLongText(), proxyChannel.id().asLongText()});
                    agentChannel.writeAndFlush(
                            new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                                    tcpConnectionInfo.getTargetAddressType()))
                            .addListener(ChannelFutureListener.CLOSE);
                });
    }
}
