package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
class SocksAgentPooledProxyChannelFactory implements PooledObjectFactory<Channel> {
    private final Bootstrap socksProxyTcpBootstrap;
    private final AgentConfiguration agentConfiguration;
    private GenericObjectPool<Channel> pool;

    public SocksAgentPooledProxyChannelFactory(Bootstrap socksProxyTcpBootstrap, AgentConfiguration agentConfiguration) {
        this.socksProxyTcpBootstrap = socksProxyTcpBootstrap;
        this.agentConfiguration = agentConfiguration;
    }

    void init(GenericObjectPool<Channel> pool) {
        this.pool = pool;
    }

    @Override
    public PooledObject<Channel> makeObject() throws Exception {
        PpaassLogger.INSTANCE.debug(() -> "Begin to create proxy channel object.");
        var proxyChannelConnectFuture = this.socksProxyTcpBootstrap
                .connect(this.agentConfiguration.getProxyHost(), this.agentConfiguration.getProxyPort())
                .sync();
        proxyChannelConnectFuture
                .get(this.agentConfiguration.getProxyChannelPoolAcquireTimeoutMillis(), TimeUnit.MILLISECONDS);
        var channel = proxyChannelConnectFuture.channel();
        channel.attr(ISocksAgentConst.IProxyChannelAttr.CHANNEL_POOL).setIfAbsent(this.pool);
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
        try {
            proxyChannel.close().sync();
        } catch (Exception e) {
            PpaassLogger.INSTANCE.debug(() -> "Fail to close proxy channel on destroy proxy channel = {}.",
                    () -> new Object[]{proxyChannel.id().asLongText(), e});
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
    }

    @Override
    public void passivateObject(PooledObject<Channel> pooledObject) throws Exception {
        var proxyChannel = pooledObject.getObject();
        proxyChannel.flush();
        proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.AGENT_CHANNEL).set(null);
        PpaassLogger.INSTANCE.debug(() -> "Passivate proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
    }
}
