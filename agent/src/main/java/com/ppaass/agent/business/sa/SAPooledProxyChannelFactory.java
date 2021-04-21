package com.ppaass.agent.business.sa;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.exception.PpaassException;
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
        int totalObjNumber = this.pool.getNumIdle() + this.pool.getNumActive();
        while (totalObjNumber >= this.pool.getMaxTotal()) {
            synchronized (SAPooledProxyChannelFactory.class) {
                this.pool.wait(1000);
            }
            totalObjNumber = this.pool.getNumIdle() + this.pool.getNumActive();
        }
        var proxyChannelConnectFuture = this.socksProxyTcpChannelBootstrap
                .connect(this.agentConfiguration.getProxyHost(), this.agentConfiguration.getProxyPort());
        proxyChannelConnectFuture.syncUninterruptibly()
                .get(this.agentConfiguration.getProxyChannelPoolAcquireTimeoutMillis(), TimeUnit.MILLISECONDS);
        if (!proxyChannelConnectFuture.isSuccess()) {
            PpaassLogger.INSTANCE.error(() -> "Fail to create proxy channel because of exception.",
                    () -> new Object[]{proxyChannelConnectFuture.cause()});
            throw new PpaassException("Fail to create proxy channel because of exception.",
                    proxyChannelConnectFuture.cause());
        }
        var channel = proxyChannelConnectFuture.channel();
        channel.attr(ISAConstant.IProxyChannelConstant.CHANNEL_POOL).set(this.pool);
        channel.attr(ISAConstant.IProxyChannelConstant.CLOSED_ALREADY).set(false);
        PpaassLogger.INSTANCE.debug(() -> "Success create proxy channel object, proxy channel = {}.",
                () -> new Object[]{channel.id().asLongText()});
        return new DefaultPooledObject<>(channel);
    }

    @Override
    public void destroyObject(PooledObject<Channel> pooledObject) throws Exception {
        var proxyChannel = pooledObject.getObject();
        PpaassLogger.INSTANCE.trace(() -> "Begin to destroy proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
        var closedAlready = proxyChannel.attr(ISAConstant.IProxyChannelConstant.CLOSED_ALREADY).get();
        if (!closedAlready) {
            PpaassLogger.INSTANCE.debug(() -> "Channel still not close, invoke close on channel, proxy channel = {}.",
                    () -> new Object[]{proxyChannel.id().asLongText()});
            proxyChannel.flush();
            proxyChannel.close().syncUninterruptibly();
        }
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
        if (!proxyChannel.isActive()) {
            PpaassLogger.INSTANCE.error(() -> "Proxy channel is not active, proxy channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText()});
            throw new PpaassException("Proxy channel is not active");
        }
    }

    @Override
    public void passivateObject(PooledObject<Channel> pooledObject) throws Exception {
        var proxyChannel = pooledObject.getObject();
        proxyChannel.flush();
        var agentChannel = proxyChannel.attr(ISAConstant.IProxyChannelConstant.AGENT_CHANNEL).get();
        proxyChannel.attr(ISAConstant.IProxyChannelConstant.AGENT_CHANNEL).set(null);
        if (agentChannel != null) {
            PpaassLogger.INSTANCE.debug(() -> "Passivate proxy channel object, proxy channel = {}, agent channel = {}.",
                    () -> new Object[]{proxyChannel.id().asLongText(),
                            agentChannel.id().asLongText()});
            return;
        }
        PpaassLogger.INSTANCE.debug(() -> "Passivate proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
    }
}
