package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentResourceManager;
import com.ppaass.common.exception.PpaassException;
import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
class SAProxyResourceManager implements IAgentResourceManager {
    private final SAUdpChannelInitializer SAUdpChannelInitializer;
    private final SATcpChannelInitializer SATcpChannelInitializer;
    private final AgentConfiguration agentConfiguration;
    private Bootstrap proxyUdpChannelBootstrap;
    private Bootstrap proxyTcpChannelBootstrap;
    private GenericObjectPool<Channel> proxyTcpChannelPool;
    private final ReentrantReadWriteLock reentrantReadWriteLock;

    public SAProxyResourceManager(
            SAUdpChannelInitializer SAUdpChannelInitializer,
            SATcpChannelInitializer SATcpChannelInitializer,
            AgentConfiguration agentConfiguration) {
        this.SAUdpChannelInitializer = SAUdpChannelInitializer;
        this.SATcpChannelInitializer = SATcpChannelInitializer;
        this.agentConfiguration = agentConfiguration;
        this.reentrantReadWriteLock = new ReentrantReadWriteLock();
    }

    public GenericObjectPool<Channel> getProxyTcpChannelPool() {
        try {
            this.reentrantReadWriteLock.readLock().lock();
            return this.proxyTcpChannelPool;
        } finally {
            this.reentrantReadWriteLock.readLock().unlock();
        }
    }

    public Bootstrap getProxyUdpChannelBootstrap() {
        try {
            this.reentrantReadWriteLock.readLock().lock();
            return this.proxyUdpChannelBootstrap;
        } finally {
            this.reentrantReadWriteLock.readLock().unlock();
        }
    }

    public void prepareResources() {
        try {
            this.reentrantReadWriteLock.writeLock().lock();
            this.proxyTcpChannelBootstrap = this.createProxyTcpChannelBootstrap();
            this.proxyUdpChannelBootstrap = this.createProxyUdpChannelBootstrap();
            this.proxyTcpChannelPool = this.createSocksProxyTcpChannelPool(this.proxyTcpChannelBootstrap);
        } finally {
            this.reentrantReadWriteLock.writeLock().unlock();
        }
    }

    public void destroyResources() {
        try {
            this.reentrantReadWriteLock.writeLock().lock();
            if (this.proxyUdpChannelBootstrap != null) {
                this.proxyUdpChannelBootstrap.config().group().shutdownGracefully();
            }
            if (this.proxyTcpChannelBootstrap != null) {
                this.proxyTcpChannelBootstrap.config().group().shutdownGracefully();
            }
            if (this.proxyTcpChannelPool != null) {
                this.proxyTcpChannelPool.close();
                this.proxyTcpChannelPool.clear();
            }
            this.proxyTcpChannelBootstrap = null;
            this.proxyUdpChannelBootstrap = null;
            this.proxyTcpChannelPool = null;
        } finally {
            this.reentrantReadWriteLock.writeLock().unlock();
        }
    }

    private Bootstrap createProxyUdpChannelBootstrap() {
        var result = new Bootstrap();
        var socksProxyUdpLoopGroup = new NioEventLoopGroup(
                this.agentConfiguration.getAgentUdpThreadNumber());
        result.group(socksProxyUdpLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.AUTO_READ, true)
                .handler(this.SAUdpChannelInitializer);
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
        result.handler(this.SATcpChannelInitializer);
        return result;
    }

    private GenericObjectPool<Channel> createSocksProxyTcpChannelPool(Bootstrap proxyTcpChannelBootstrap) {
        var socksAgentPooledProxyChannelFactory =
                new SAPooledProxyChannelFactory(proxyTcpChannelBootstrap, agentConfiguration);
        var config = new GenericObjectPoolConfig<Channel>();
        config.setMaxIdle(agentConfiguration.getProxyChannelPoolMaxIdleSize());
        config.setMaxTotal(agentConfiguration.getProxyChannelPoolMaxTotalSize());
        config.setMinIdle(agentConfiguration.getProxyChannelPoolMinIdleSize());
        config.setBlockWhenExhausted(true);
        config.setEvictionPolicy(new DefaultEvictionPolicy<>());
        config.setTestOnBorrow(true);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);
        config.setTimeBetweenEvictionRunsMillis(agentConfiguration.getProxyChannelPoolTimeBetweenEvictionRunsMillis());
        config.setJmxEnabled(false);
        var result = new GenericObjectPool<>(socksAgentPooledProxyChannelFactory, config);
        socksAgentPooledProxyChannelFactory.attachPool(result);
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
