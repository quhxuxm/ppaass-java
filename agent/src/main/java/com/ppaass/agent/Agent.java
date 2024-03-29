package com.ppaass.agent;

import com.ppaass.agent.business.AgentChannelInitializer;
import com.ppaass.common.exception.PpaassException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class Agent {
    private static final Logger logger = LoggerFactory.getLogger(Agent.class);
    private Channel serverSocketChannel;
    private EventLoopGroup masterThreadGroup;
    private EventLoopGroup workerThreadGroup;
    private final AgentConfiguration agentConfiguration;
    private final AgentChannelInitializer agentChannelInitializer;
    private final Set<IAgentResourceManager> resourceManagers;

    public Agent(AgentConfiguration agentConfiguration,
                 AgentChannelInitializer agentChannelInitializer, Set<IAgentResourceManager> resourceManagers) {
        this.agentConfiguration = agentConfiguration;
        this.agentChannelInitializer = agentChannelInitializer;
        this.resourceManagers = resourceManagers;
    }

    public void start() {
        var newServerBootstrap = new ServerBootstrap();
        var newTcpMasterThreadGroup = new NioEventLoopGroup(
                this.agentConfiguration.getAgentTcpMasterThreadNumber());
        var newTcpWorkerThreadGroup = new NioEventLoopGroup(
                agentConfiguration.getAgentTcpWorkerThreadNumber());
        newServerBootstrap.group(newTcpMasterThreadGroup, newTcpWorkerThreadGroup);
        newServerBootstrap.channel(NioServerSocketChannel.class);
        newServerBootstrap
                .option(ChannelOption.SO_BACKLOG,
                        agentConfiguration.getAgentTcpSoBacklog());
        newServerBootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        newServerBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        newServerBootstrap.childOption(ChannelOption.AUTO_CLOSE, true);
        newServerBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        newServerBootstrap.childOption(ChannelOption.AUTO_READ, true);
        newServerBootstrap.childOption(ChannelOption.SO_LINGER,
                agentConfiguration.getAgentTcpSoLinger());
        newServerBootstrap
                .childOption(ChannelOption.SO_RCVBUF,
                        agentConfiguration.getAgentTcpSoRcvbuf());
        newServerBootstrap
                .childOption(ChannelOption.SO_SNDBUF,
                        agentConfiguration.getAgentTcpSoSndbuf());
        newServerBootstrap.childHandler(this.agentChannelInitializer);
        var agentTcpPort = this.agentConfiguration.getTcpPort();
        ChannelFuture channelFuture = null;
        try {
            channelFuture = newServerBootstrap.bind(agentTcpPort).sync();
        } catch (InterruptedException e) {
            logger
                    .error("Fail to start ppaass because of exception", e);
            throw new PpaassException("Fail to start ppaass because of exception", e);
        }
        this.serverSocketChannel = channelFuture.channel();
        this.masterThreadGroup = newTcpMasterThreadGroup;
        this.workerThreadGroup = newTcpWorkerThreadGroup;
        this.resourceManagers.forEach(IAgentResourceManager::prepareResources);
    }

    public void stop() {
        if (this.serverSocketChannel != null) {
            try {
                this.serverSocketChannel.close().sync();
            } catch (InterruptedException e) {
                logger
                        .error("Fail to stop ppaass because of exception", e);
            }
        }
        if (this.masterThreadGroup != null) {
            this.masterThreadGroup.shutdownGracefully();
        }
        if (this.workerThreadGroup != null) {
            this.workerThreadGroup.shutdownGracefully();
        }
        this.serverSocketChannel = null;
        this.masterThreadGroup = null;
        this.workerThreadGroup = null;
        this.resourceManagers.forEach(IAgentResourceManager::destroyResources);
    }
}
