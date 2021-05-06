package com.ppaass.agent.business;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.EvictionConfig;
import org.apache.commons.pool2.impl.EvictionPolicy;

public class ProxyTcpChannelPoolEvictionPolicy implements EvictionPolicy<Channel> {
    private static final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();

    @Override
    public boolean evict(EvictionConfig config, PooledObject<Channel> underTest, int idleCount) {
//        boolean poolSizeCondition = super.evict(config, underTest, idleCount);
        Channel proxyTcpChannel = underTest.getObject();
        if (!proxyTcpChannel.isOpen()) {
            logger.error(() -> "Mark proxy channel should be evict as it is not open, proxy channel={}",
                    () -> new Object[]{proxyTcpChannel.id().asLongText()});
            return true;
        }
        if (!proxyTcpChannel.isActive()) {
            logger.error(() -> "Mark proxy channel should be evict as it is not active, proxy channel={}",
                    () -> new Object[]{proxyTcpChannel.id().asLongText()});
            return true;
        }
        ChannelFuture testResultFuture = proxyTcpChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).syncUninterruptibly();
        if (!testResultFuture.isSuccess()) {
            logger.error(() -> "Mark proxy channel should be evict as fail to write, proxy channel={}",
                    () -> new Object[]{proxyTcpChannel.id().asLongText()});
            return true;
        }
        logger.debug(() -> "Proxy channel still available, proxy channel={}",
                () -> new Object[]{proxyTcpChannel.id().asLongText()});
        return false;
    }
}
