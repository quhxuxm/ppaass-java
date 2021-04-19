package com.ppaass.agent.business.ha;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentResourceManager;
import com.ppaass.common.exception.PpaassException;
import com.ppaass.common.handler.AgentMessageEncoder;
import com.ppaass.common.handler.PrintExceptionHandler;
import com.ppaass.common.handler.ProxyMessageDecoder;
import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.Lz4FrameDecoder;
import io.netty.handler.codec.compression.Lz4FrameEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseDecoder;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
class HAProxyResourceManager implements IAgentResourceManager {
    private final AgentConfiguration agentConfiguration;
    private Bootstrap proxyTcpChannelBootstrapForHttp;
    private Bootstrap proxyTcpChannelBootstrapForHttps;
    private GenericObjectPool<Channel> proxyTcpChannelPoolForHttp;
    private GenericObjectPool<Channel> proxyTcpChannelPoolForHttps;
    private final ReentrantReadWriteLock reentrantReadWriteLock;
    private final HASendPureDataToAgentHandler haSendPureDataToAgentHandler;
    private final HAProxyMessageBodyTypeHandler haProxyMessageBodyTypeHandler;

    public HAProxyResourceManager(
            AgentConfiguration agentConfiguration,
            HASendPureDataToAgentHandler haSendPureDataToAgentHandler,
            HAProxyMessageBodyTypeHandler haProxyMessageBodyTypeHandler) {
        this.agentConfiguration = agentConfiguration;
        this.haSendPureDataToAgentHandler = haSendPureDataToAgentHandler;
        this.haProxyMessageBodyTypeHandler = haProxyMessageBodyTypeHandler;
        this.reentrantReadWriteLock = new ReentrantReadWriteLock();
    }

    public GenericObjectPool<Channel> getProxyTcpChannelPoolForHttp() {
        try {
            this.reentrantReadWriteLock.readLock().lock();
            return this.proxyTcpChannelPoolForHttp;
        } finally {
            this.reentrantReadWriteLock.readLock().unlock();
        }
    }

    public GenericObjectPool<Channel> getProxyTcpChannelPoolForHttps() {
        try {
            this.reentrantReadWriteLock.readLock().lock();
            return this.proxyTcpChannelPoolForHttps;
        } finally {
            this.reentrantReadWriteLock.readLock().unlock();
        }
    }

    public void prepareResources() {
        try {
            this.reentrantReadWriteLock.writeLock().lock();
            this.proxyTcpChannelBootstrapForHttp = this.createProxyTcpChannelBootstrapForHttp();
            this.proxyTcpChannelBootstrapForHttps = this.createProxyTcpChannelBootstrapForHttps();
            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    this.reentrantReadWriteLock.writeLock().lock();
                    this.proxyTcpChannelPoolForHttp =
                            this.createHttpOrHttpsProxyTcpChannelPool(this.proxyTcpChannelBootstrapForHttp);
                    this.proxyTcpChannelPoolForHttps =
                            this.createHttpOrHttpsProxyTcpChannelPool(this.proxyTcpChannelBootstrapForHttps);
                } finally {
                    this.reentrantReadWriteLock.writeLock().unlock();
                }
            });
        } finally {
            this.reentrantReadWriteLock.writeLock().unlock();
        }
    }

    public void destroyResources() {
        try {
            this.reentrantReadWriteLock.writeLock().lock();
            if (this.proxyTcpChannelBootstrapForHttp != null) {
                this.proxyTcpChannelBootstrapForHttp.config().group().shutdownGracefully();
            }
            if (this.proxyTcpChannelBootstrapForHttps != null) {
                this.proxyTcpChannelBootstrapForHttps.config().group().shutdownGracefully();
            }
            if (this.proxyTcpChannelPoolForHttp != null) {
                this.proxyTcpChannelPoolForHttp.close();
                this.proxyTcpChannelPoolForHttp.clear();
            }
            if (this.proxyTcpChannelPoolForHttps != null) {
                this.proxyTcpChannelPoolForHttps.close();
                this.proxyTcpChannelPoolForHttps.clear();
            }
            this.proxyTcpChannelBootstrapForHttp = null;
            this.proxyTcpChannelBootstrapForHttps = null;
            this.proxyTcpChannelPoolForHttps = null;
            this.proxyTcpChannelPoolForHttp = null;
        } finally {
            this.reentrantReadWriteLock.writeLock().unlock();
        }
    }

    private Bootstrap createProxyTcpChannelBootstrapForHttp() {
        var proxyTcpLoopGroup = new NioEventLoopGroup(agentConfiguration.getProxyTcpThreadNumber());
        Bootstrap result = new Bootstrap();
        result.group(proxyTcpLoopGroup);
        result.channel(NioSocketChannel.class);
        result.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                agentConfiguration.getProxyTcpConnectionTimeout());
        result.option(ChannelOption.SO_KEEPALIVE, true);
        result.option(ChannelOption.SO_REUSEADDR, true);
        result.option(ChannelOption.AUTO_READ, true);
        result.option(ChannelOption.AUTO_CLOSE, true);
        result.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        result.option(ChannelOption.TCP_NODELAY, true);
        result.option(ChannelOption.SO_LINGER,
                agentConfiguration.getProxyTcpSoLinger());
        result.option(ChannelOption.SO_RCVBUF,
                agentConfiguration.getProxyTcpSoRcvbuf());
        result.option(ChannelOption.SO_SNDBUF,
                agentConfiguration.getProxyTcpSoSndbuf());
        result.handler(new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel proxyChannel) {
                var proxyChannelPipeline = proxyChannel.pipeline();
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameDecoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                        0, 4, 0,
                        4));
                proxyChannelPipeline.addLast(new ProxyMessageDecoder(
                        agentConfiguration.getAgentPrivateKey()));
                proxyChannelPipeline.addLast(haProxyMessageBodyTypeHandler);
                proxyChannelPipeline.addLast(new HAExtractPureDataDecoder());
                proxyChannelPipeline.addLast(new HttpResponseDecoder());
                proxyChannelPipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE, true));
                proxyChannelPipeline.addLast(haSendPureDataToAgentHandler);
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameEncoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldPrepender(4));
                proxyChannelPipeline.addLast(new AgentMessageEncoder(
                        agentConfiguration.getProxyPublicKey()));
                proxyChannelPipeline.addLast(PrintExceptionHandler.INSTANCE);
            }
        });
        return result;
    }

    private Bootstrap createProxyTcpChannelBootstrapForHttps() {
        var proxyTcpLoopGroup = new NioEventLoopGroup(agentConfiguration.getProxyTcpThreadNumber());
        Bootstrap result = new Bootstrap();
        result.group(proxyTcpLoopGroup);
        result.channel(NioSocketChannel.class);
        result.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                agentConfiguration.getProxyTcpConnectionTimeout());
        result.option(ChannelOption.SO_KEEPALIVE, true);
        result.option(ChannelOption.SO_REUSEADDR, true);
        result.option(ChannelOption.AUTO_READ, true);
        result.option(ChannelOption.AUTO_CLOSE, true);
        result.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        result.option(ChannelOption.TCP_NODELAY, true);
        result.option(ChannelOption.SO_LINGER,
                agentConfiguration.getProxyTcpSoLinger());
        result.option(ChannelOption.SO_RCVBUF,
                agentConfiguration.getProxyTcpSoRcvbuf());
        result.option(ChannelOption.SO_SNDBUF,
                agentConfiguration.getProxyTcpSoSndbuf());
        result.handler(new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel proxyChannel) {
                var proxyChannelPipeline = proxyChannel.pipeline();
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameDecoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                        0, 4, 0,
                        4));
                proxyChannelPipeline.addLast(new ProxyMessageDecoder(
                        agentConfiguration.getAgentPrivateKey()));
                proxyChannelPipeline.addLast(haProxyMessageBodyTypeHandler);
                proxyChannelPipeline.addLast(new HAExtractPureDataDecoder());
                proxyChannelPipeline.addLast(haSendPureDataToAgentHandler);
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameEncoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldPrepender(4));
                proxyChannelPipeline.addLast(new AgentMessageEncoder(
                        agentConfiguration.getProxyPublicKey()));
                proxyChannelPipeline.addLast(PrintExceptionHandler.INSTANCE);
            }
        });
        return result;
    }

    private GenericObjectPool<Channel> createHttpOrHttpsProxyTcpChannelPool(Bootstrap bootstrap) {
        var socksAgentPooledProxyChannelFactory =
                new HAPooledProxyChannelFactory(bootstrap, agentConfiguration);
        var config = new GenericObjectPoolConfig<Channel>();
        config.setMaxIdle(agentConfiguration.getProxyChannelPoolMaxIdleSize());
        config.setMaxTotal(agentConfiguration.getProxyChannelPoolMaxTotalSize());
        config.setMinIdle(agentConfiguration.getProxyChannelPoolMinIdleSize());
        config.setBlockWhenExhausted(true);
        config.setEvictionPolicy(new DefaultEvictionPolicy<>());
        config.setFairness(true);
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
