package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.exception.PpaassException;
import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SocksAgentConfigure {
    @Bean
    public Bootstrap socksProxyTcpBootstrap(
            EventLoopGroup proxyTcpLoopGroup, AgentConfiguration agentConfiguration,
            SocksAgentProxyChannelInitializer proxyChannelInitializer) {
        var result = new Bootstrap();
        result.group(proxyTcpLoopGroup);
        result.channel(NioSocketChannel.class);
        result.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                agentConfiguration.getProxyTcpConnectionTimeout());
        result.option(ChannelOption.SO_KEEPALIVE, true);
        result.option(ChannelOption.AUTO_READ, true);
        result.option(ChannelOption.AUTO_CLOSE, true);
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
        result.handler(proxyChannelInitializer);
        return result;
    }

    @Bean
    public Bootstrap socksProxyUdpBootstrap(AgentConfiguration agentConfiguration,
                                            SocksAgentUdpChannelInitializer udpChannelInitializer) {
        var result = new Bootstrap();
        var socksProxyUdpLoopGroup = new NioEventLoopGroup(
                agentConfiguration.getAgentUdpThreadNumber());
        result.group(socksProxyUdpLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, false)
                .option(ChannelOption.AUTO_READ, true)
                .handler(udpChannelInitializer);
        return result;
    }

    @Bean
    public GenericObjectPool<Channel> socksProxyTcpChannelPool(
            SocksAgentPooledProxyChannelFactory socksAgentPooledProxyChannelFactory,
            AgentConfiguration agentConfiguration) {
        var config = new GenericObjectPoolConfig<Channel>();
        config.setMaxIdle(agentConfiguration.getProxyChannelPoolMaxIdleSize());
        config.setMaxTotal(agentConfiguration.getProxyChannelPoolMaxTotalSize());
        config.setMinIdle(agentConfiguration.getProxyChannelPoolMinIdleSize());
        config.setBlockWhenExhausted(true);
        config.setEvictionPolicy(new DefaultEvictionPolicy<>());
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(true);
        config.setTimeBetweenEvictionRunsMillis(agentConfiguration.getProxyChannelPoolTimeBetweenEvictionRunsMillis());
        config.setJmxEnabled(false);
        var result = new GenericObjectPool<>(socksAgentPooledProxyChannelFactory, config);
        socksAgentPooledProxyChannelFactory.init(result);
        try {
            result.preparePool();
        } catch (Exception e) {
            PpaassLogger.INSTANCE
                    .error(() -> "Fail to initialize proxy channel pool because of exception.", () -> new Object[]{e});
            throw new PpaassException("Fail to initialize proxy channel pool.", e);
        }
        return result;
    }
}
