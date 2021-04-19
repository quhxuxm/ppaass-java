package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.util.concurrent.TimeUnit;

class SAPooledProxyChannelFactory implements PooledObjectFactory<Channel> {
    private final Bootstrap socksProxyTcpChannelBootstrap;
    private final AgentConfiguration agentConfiguration;
    private GenericObjectPool<Channel> pool;

    public SAPooledProxyChannelFactory(Bootstrap socksProxyTcpChannelBootstrap,
                                       AgentConfiguration agentConfiguration) {
        this.socksProxyTcpChannelBootstrap = socksProxyTcpChannelBootstrap;
        this.agentConfiguration = agentConfiguration;
    }

    void attachPool(GenericObjectPool<Channel> pool) {
        this.pool = pool;
    }

    @Override
    public PooledObject<Channel> makeObject() throws Exception {
        PpaassLogger.INSTANCE.debug(() -> "Begin to create proxy channel object.");
        var proxyChannelConnectFuture = this.socksProxyTcpChannelBootstrap
                .connect(this.agentConfiguration.getProxyHost(), this.agentConfiguration.getProxyPort())
                .syncUninterruptibly();
        proxyChannelConnectFuture
                .get(this.agentConfiguration.getProxyChannelPoolAcquireTimeoutMillis(), TimeUnit.MILLISECONDS);
        var channel = proxyChannelConnectFuture.channel();
        channel.attr(ISAConstant.IProxyChannelConstant.CHANNEL_POOL).set(this.pool);
        PpaassLogger.INSTANCE.debug(() -> "Success create proxy channel object, proxy channel = {}.",
                () -> new Object[]{channel.id().asLongText()});
        return new DefaultPooledObject<>(channel);
    }

    @Override
    public void destroyObject(PooledObject<Channel> pooledObject) throws Exception {
        var proxyChannel = pooledObject.getObject();
        PpaassLogger.INSTANCE.trace(() -> "Begin to destroy proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
        proxyChannel.flush();
        proxyChannel.close().syncUninterruptibly();
        proxyChannel.deregister().syncUninterruptibly();
        PpaassLogger.INSTANCE.debug(() -> "Success destroy proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
    }

    @Override
    public boolean validateObject(PooledObject<Channel> pooledObject) {
        var proxyChannel = pooledObject.getObject();
        PpaassLogger.INSTANCE.trace(() -> "Begin to validate proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
        var validStatus = proxyChannel.isActive();
        PpaassLogger.INSTANCE.trace(() -> "Proxy channel valid status = {}, proxy channel = {}.",
                () -> new Object[]{validStatus, proxyChannel.id().asLongText()});
        return validStatus;
    }

    @Override
    public void activateObject(PooledObject<Channel> pooledObject) throws Exception {
        var proxyChannel = pooledObject.getObject();
        PpaassLogger.INSTANCE.trace(() -> "Activate proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
    }

    @Override
    public void passivateObject(PooledObject<Channel> pooledObject) throws Exception {
        var proxyChannel = pooledObject.getObject();
        proxyChannel.flush();
        var agentChannel = proxyChannel.attr(ISAConstant.IProxyChannelConstant.AGENT_CHANNEL).get();
        if (agentChannel != null) {
            agentChannel.attr(ISAConstant.IAgentChannelConstant.TCP_CONNECTION_INFO).set(null);
        }
        proxyChannel.attr(ISAConstant.IProxyChannelConstant.AGENT_CHANNEL).set(null);
        PpaassLogger.INSTANCE.debug(() -> "Passivate proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
    }
}