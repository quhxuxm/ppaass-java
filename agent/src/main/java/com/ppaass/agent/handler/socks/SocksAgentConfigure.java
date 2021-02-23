package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SocksAgentConfigure {
    @Bean
    public Bootstrap socksProxyTcpBootstrap(
            EventLoopGroup proxyTcpLoopGroup, AgentConfiguration agentConfiguration,
            SocksAgentProxyTcpChannelInitializer proxyChannelInitializer) {
        var result = new Bootstrap();
        result.group(proxyTcpLoopGroup);
        result.channel(NioSocketChannel.class);
        result.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                agentConfiguration.getProxyTcpConnectionTimeout());
        result.option(ChannelOption.SO_KEEPALIVE, true);
        result.option(ChannelOption.AUTO_READ, false);
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
        result.handler(proxyChannelInitializer);
        return result;
    }

    @Bean
    public Bootstrap socksProxyUdpBootstrap(AgentConfiguration agentConfiguration,
                                            SocksAgentProxyUdpChannelInitializer proxyChannelInitializer) {
        var result = new Bootstrap();
        var socksProxyUdpLoopGroup = new NioEventLoopGroup(
                agentConfiguration.getAgentUdpThreadNumber());
        result.group(socksProxyUdpLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(proxyChannelInitializer);
        return result;
    }
}
