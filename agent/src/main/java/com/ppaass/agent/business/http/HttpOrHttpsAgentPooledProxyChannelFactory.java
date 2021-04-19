package com.ppaass.agent.business.http;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.util.concurrent.TimeUnit;

class HttpOrHttpsAgentPooledProxyChannelFactory implements PooledObjectFactory<Channel> {
    private final Bootstrap bootstrap;
    private final AgentConfiguration agentConfiguration;
    private GenericObjectPool<Channel> pool;

    public HttpOrHttpsAgentPooledProxyChannelFactory(Bootstrap bootstrap,
                                                     AgentConfiguration agentConfiguration) {
        this.bootstrap = bootstrap;
        this.agentConfiguration = agentConfiguration;
    }

    void attachPool(GenericObjectPool<Channel> pool) {
        this.pool = pool;
    }

    @Override
    public PooledObject<Channel> makeObject() throws Exception {
        PpaassLogger.INSTANCE.debug(() -> "Begin to create proxy channel object.");
        var proxyChannelConnectFuture = this.bootstrap
                .connect(this.agentConfiguration.getProxyHost(), this.agentConfiguration.getProxyPort())
                .syncUninterruptibly();
        proxyChannelConnectFuture
                .get(this.agentConfiguration.getProxyChannelPoolAcquireTimeoutMillis(), TimeUnit.MILLISECONDS);
        var channel = proxyChannelConnectFuture.channel();
        channel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL).set(this.pool);
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
        var resultFuture = proxyChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).syncUninterruptibly();
        var validStatus = resultFuture.isSuccess();
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
        var httpConnectionInfo = proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO).get();
        if (httpConnectionInfo != null) {
            httpConnectionInfo.getAgentChannel().attr(IHttpAgentConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO)
                    .set(null);
        }
        proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO).set(null);
        PpaassLogger.INSTANCE.debug(() -> "Passivate proxy channel object, proxy channel = {}.",
                () -> new Object[]{proxyChannel.id().asLongText()});
    }
}
