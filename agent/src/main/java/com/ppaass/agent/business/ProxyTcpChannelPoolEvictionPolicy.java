package com.ppaass.agent.business;

import io.netty.channel.Channel;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.EvictionConfig;

public class ProxyTcpChannelPoolEvictionPolicy extends DefaultEvictionPolicy<Channel> {
    @Override
    public boolean evict(EvictionConfig config, PooledObject<Channel> underTest, int idleCount) {
        boolean poolSizeCondition = super.evict(config, underTest, idleCount);
        Channel proxyTcpChannel = underTest.getObject();
        return !(proxyTcpChannel.isOpen() || proxyTcpChannel.isActive()) && poolSizeCondition;
    }
}
