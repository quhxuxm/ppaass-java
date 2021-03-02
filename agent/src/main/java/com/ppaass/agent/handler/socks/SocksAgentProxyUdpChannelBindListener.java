package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.agent.handler.socks.bo.SocksAgentUdpConnectionInfo;
import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

import java.net.InetSocketAddress;

class SocksAgentProxyUdpChannelBindListener implements ChannelFutureListener {
    static {
        PpaassLogger.INSTANCE.register(SocksAgentProxyUdpChannelBindListener.class);
    }

    private final Channel agentTcpChannel;
    private final Bootstrap socksProxyTcpBootstrap;
    private final AgentConfiguration agentConfiguration;
    private final Socks5CommandRequest socks5CommandRequest;

    public SocksAgentProxyUdpChannelBindListener(Channel agentTcpChannel,
                                                 Bootstrap socksProxyTcpBootstrap,
                                                 AgentConfiguration agentConfiguration,
                                                 Socks5CommandRequest socks5CommandRequest) {
        this.agentTcpChannel = agentTcpChannel;
        this.socksProxyTcpBootstrap = socksProxyTcpBootstrap;
        this.agentConfiguration = agentConfiguration;
        this.socks5CommandRequest = socks5CommandRequest;
    }

    @Override
    public void operationComplete(ChannelFuture agentUdpChannelFuture) throws Exception {
        if (!agentUdpChannelFuture.isSuccess()) {
            PpaassLogger.INSTANCE.error(SocksAgentProxyUdpChannelBindListener.class,
                    () -> "Fail to associate UDP tunnel for agent channel because of exception, agent channel = {}",
                    () -> new Object[]{
                            agentTcpChannel.id().asLongText(),
                            agentUdpChannelFuture.cause()
                    });
            return;
        }
        var agentUdpChannel = agentUdpChannelFuture.channel();
        var agentUdpAddress =
                (InetSocketAddress) (agentUdpChannelFuture.channel()
                        .localAddress());
        var proxyTcpChannelForUdpTransfer = this.socksProxyTcpBootstrap
                .connect(this.agentConfiguration.getProxyHost(),
                        this.agentConfiguration.getProxyPort())
                .syncUninterruptibly().channel();
        var udpConnectionInfo = new SocksAgentUdpConnectionInfo(
                agentUdpAddress.getPort(),
                this.socks5CommandRequest.dstAddr(),
                this.socks5CommandRequest.dstPort(),
                this.agentConfiguration.getUserToken(),
                this.agentTcpChannel,
                agentUdpChannel,
                proxyTcpChannelForUdpTransfer
        );
        this.agentTcpChannel.attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO)
                .setIfAbsent(udpConnectionInfo);
        proxyTcpChannelForUdpTransfer.attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO)
                .setIfAbsent(udpConnectionInfo);
        agentUdpChannel.attr(ISocksAgentConst.SOCKS_UDP_CONNECTION_INFO)
                .setIfAbsent(udpConnectionInfo);
        this.agentTcpChannel.writeAndFlush(new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS,
                Socks5AddressType.IPv4, IAgentConst.LOCAL_IP_ADDRESS,
                udpConnectionInfo.getAgentUdpPort()));
    }
}
