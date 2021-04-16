package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.handler.AgentMessageEncoder;
import com.ppaass.common.handler.PrintExceptionHandler;
import com.ppaass.common.handler.ProxyMessageDecoder;
import com.ppaass.common.log.PpaassLogger;
import io.netty.channel.Channel;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.Lz4FrameDecoder;
import io.netty.handler.codec.compression.Lz4FrameEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
class SocksAgentProxyTcpChannelPoolInitializer extends AbstractChannelPoolHandler {
    private final PrintExceptionHandler printExceptionHandler;
    private final AgentConfiguration agentConfiguration;
    private final SocksAgentP2ATcpChannelHandler socksAgentP2ATcpChannelHandler;
    private FixedChannelPool channelPool;

    public void setChannelPool(FixedChannelPool channelPool) {
        this.channelPool = channelPool;
    }

    public SocksAgentProxyTcpChannelPoolInitializer(PrintExceptionHandler printExceptionHandler,
                                                    AgentConfiguration agentConfiguration,
                                                    SocksAgentP2ATcpChannelHandler socksAgentP2ATcpChannelHandler) {
        this.printExceptionHandler = printExceptionHandler;
        this.agentConfiguration = agentConfiguration;
        this.socksAgentP2ATcpChannelHandler = socksAgentP2ATcpChannelHandler;
    }

    @Override
    public void channelAcquired(Channel proxyChannel) {
        PpaassLogger.INSTANCE.info(
                () -> "Proxy channel acquired, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText()});
    }

    @Override
    public void channelReleased(Channel proxyChannel) {
        PpaassLogger.INSTANCE.info(
                () -> "Proxy channel released,  proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText()});
    }

    @Override
    public void channelCreated(Channel proxyChannel) throws Exception {
        PpaassLogger.INSTANCE.info(
                () -> "Proxy channel created, proxy channel = " + proxyChannel.id().asLongText());
        proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.AGENT_CHANNELS).setIfAbsent(new ConcurrentHashMap<>());
        proxyChannel.attr(ISocksAgentConst.IProxyChannelAttr.CHANNEL_POOL).setIfAbsent(this.channelPool);
        var proxyChannelPipeline = proxyChannel.pipeline();
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameDecoder());
        }
        proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        proxyChannelPipeline.addLast(new ProxyMessageDecoder(agentConfiguration.getAgentPrivateKey()));
        proxyChannelPipeline.addLast(this.socksAgentP2ATcpChannelHandler);
        if (agentConfiguration.isProxyTcpCompressEnable()) {
            proxyChannelPipeline.addLast(new Lz4FrameEncoder());
        }
        proxyChannelPipeline.addLast(new LengthFieldPrepender(4));
        proxyChannelPipeline.addLast(new AgentMessageEncoder(agentConfiguration.getProxyPublicKey()));
        proxyChannelPipeline.addLast(this.printExceptionHandler);
    }
}
