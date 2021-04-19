package com.ppaass.agent.business.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.agent.business.socks.bo.SocksAgentUdpConnectionInfo;
import com.ppaass.common.log.PpaassLogger;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

import java.net.InetSocketAddress;

class SocksAgentUdpBindListener implements ChannelFutureListener {
    private final Channel agentTcpChannel;
    private final Channel proxyTcpChannel;
    private final AgentConfiguration agentConfiguration;
    private final Socks5CommandRequest socks5CommandRequest;

    public SocksAgentUdpBindListener(Channel agentTcpChannel,
                                     Channel proxyTcpChannel,
                                     AgentConfiguration agentConfiguration,
                                     Socks5CommandRequest socks5CommandRequest) {
        this.agentTcpChannel = agentTcpChannel;
        this.proxyTcpChannel = proxyTcpChannel;
        this.agentConfiguration = agentConfiguration;
        this.socks5CommandRequest = socks5CommandRequest;
    }

    @Override
    public void operationComplete(ChannelFuture agentUdpChannelFuture) throws Exception {
        if (!agentUdpChannelFuture.isSuccess()) {
            PpaassLogger.INSTANCE.error(
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
        var udpConnectionInfo = new SocksAgentUdpConnectionInfo(
                agentUdpAddress.getPort(),
                this.socks5CommandRequest.dstAddr(),
                this.socks5CommandRequest.dstPort(),
                this.agentConfiguration.getUserToken(),
                this.agentTcpChannel,
                agentUdpChannel,
                this.proxyTcpChannel
        );
        this.agentTcpChannel.attr(ISocksAgentConstant.SOCKS_UDP_CONNECTION_INFO)
                .set(udpConnectionInfo);
        this.proxyTcpChannel.attr(ISocksAgentConstant.SOCKS_UDP_CONNECTION_INFO)
                .set(udpConnectionInfo);
        agentUdpChannel.attr(ISocksAgentConstant.SOCKS_UDP_CONNECTION_INFO)
                .set(udpConnectionInfo);
        this.agentTcpChannel.writeAndFlush(new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS,
                Socks5AddressType.IPv4, IAgentConst.LOCAL_IP_ADDRESS,
                udpConnectionInfo.getAgentUdpPort()));
    }
}
