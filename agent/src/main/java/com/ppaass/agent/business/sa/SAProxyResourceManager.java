package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentResourceManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.springframework.stereotype.Service;

@Service
class SAProxyResourceManager implements IAgentResourceManager {
    private final SAUdpChannelInitializer saUdpChannelInitializer;
    private final SAProxyTcpChannelInitializer saProxyTcpChannelInitializer;
    private final AgentConfiguration agentConfiguration;
    private Bootstrap proxyUdpChannelBootstrap;
    private Bootstrap proxyTcpChannelBootstrap;

    public SAProxyResourceManager(
            SAUdpChannelInitializer saUdpChannelInitializer,
            SAProxyTcpChannelInitializer saProxyTcpChannelInitializer,
            AgentConfiguration agentConfiguration) {
        this.saUdpChannelInitializer = saUdpChannelInitializer;
        this.saProxyTcpChannelInitializer = saProxyTcpChannelInitializer;
        this.agentConfiguration = agentConfiguration;
    }

    public Bootstrap getProxyUdpChannelBootstrap() {
        return this.proxyUdpChannelBootstrap;
    }

    public Bootstrap getProxyTcpChannelBootstrap() {
        return proxyTcpChannelBootstrap;
    }

    public void prepareResources() {
        this.proxyTcpChannelBootstrap = this.createProxyTcpChannelBootstrap();
        this.proxyUdpChannelBootstrap = this.createProxyUdpChannelBootstrap();
    }

    public void destroyResources() {
        if (this.proxyUdpChannelBootstrap != null) {
            this.proxyUdpChannelBootstrap.config().group().shutdownGracefully();
        }
        if (this.proxyTcpChannelBootstrap != null) {
            this.proxyTcpChannelBootstrap.config().group().shutdownGracefully();
        }
        this.proxyTcpChannelBootstrap = null;
        this.proxyUdpChannelBootstrap = null;
    }

    private Bootstrap createProxyUdpChannelBootstrap() {
        var result = new Bootstrap();
        var socksProxyUdpLoopGroup = new NioEventLoopGroup(
                this.agentConfiguration.getAgentUdpThreadNumber());
        result.group(socksProxyUdpLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.AUTO_READ, true)
                .handler(this.saUdpChannelInitializer);
        return result;
    }

    private Bootstrap createProxyTcpChannelBootstrap() {
        var proxyTcpLoopGroup = new NioEventLoopGroup(agentConfiguration.getProxyTcpThreadNumber());
        var result = new Bootstrap();
        result.group(proxyTcpLoopGroup);
        result.channel(NioSocketChannel.class);
        result.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                agentConfiguration.getProxyTcpConnectionTimeout());
        result.option(ChannelOption.SO_KEEPALIVE, true);
        result.option(ChannelOption.AUTO_READ, true);
        result.option(ChannelOption.AUTO_CLOSE, false);
        result.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        result.option(ChannelOption.TCP_NODELAY, true);
        result.option(ChannelOption.SO_REUSEADDR, true);
        result.option(ChannelOption.SO_LINGER,
                agentConfiguration.getProxyTcpSoLinger());
        result.option(ChannelOption.SO_RCVBUF,
                agentConfiguration.getProxyTcpSoRcvbuf());
        result.option(ChannelOption.SO_SNDBUF,
                agentConfiguration.getProxyTcpSoSndbuf());
        result.remoteAddress(agentConfiguration.getProxyHost(), agentConfiguration.getProxyPort());
        result.handler(this.saProxyTcpChannelInitializer);
        return result;
    }
}
