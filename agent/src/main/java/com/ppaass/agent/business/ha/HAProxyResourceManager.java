package com.ppaass.agent.business.ha;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentResourceManager;
import com.ppaass.common.constant.ICommonConstant;
import com.ppaass.common.handler.AgentMessageEncoder;
import com.ppaass.common.handler.PrintExceptionHandler;
import com.ppaass.common.handler.ProxyMessageDecoder;
import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
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
import org.springframework.stereotype.Service;

@Service
class HAProxyResourceManager implements IAgentResourceManager {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final AgentConfiguration agentConfiguration;
    private Bootstrap proxyTcpChannelBootstrapForHttp;
    private Bootstrap proxyTcpChannelBootstrapForHttps;
    private final HASendPureDataToAgentHandler haSendPureDataToAgentHandler;
    private final HAProxyMessageBodyTypeHandler haProxyMessageBodyTypeHandler;

    public HAProxyResourceManager(
            AgentConfiguration agentConfiguration,
            HASendPureDataToAgentHandler haSendPureDataToAgentHandler,
            HAProxyMessageBodyTypeHandler haProxyMessageBodyTypeHandler) {
        this.agentConfiguration = agentConfiguration;
        this.haSendPureDataToAgentHandler = haSendPureDataToAgentHandler;
        this.haProxyMessageBodyTypeHandler = haProxyMessageBodyTypeHandler;
    }

    public Bootstrap getProxyTcpChannelBootstrapForHttp() {
        return proxyTcpChannelBootstrapForHttp;
    }

    public Bootstrap getProxyTcpChannelBootstrapForHttps() {
        return proxyTcpChannelBootstrapForHttps;
    }

    public void prepareResources() {
        this.proxyTcpChannelBootstrapForHttp = this.createProxyTcpChannelBootstrapForHttp();
        this.proxyTcpChannelBootstrapForHttps = this.createProxyTcpChannelBootstrapForHttps();
    }

    public void destroyResources() {
        if (this.proxyTcpChannelBootstrapForHttp != null) {
            this.proxyTcpChannelBootstrapForHttp.config().group().shutdownGracefully();
        }
        if (this.proxyTcpChannelBootstrapForHttps != null) {
            this.proxyTcpChannelBootstrapForHttps.config().group().shutdownGracefully();
        }
        this.proxyTcpChannelBootstrapForHttp = null;
        this.proxyTcpChannelBootstrapForHttps = null;
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
        result.option(ChannelOption.AUTO_CLOSE, false);
        result.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        result.option(ChannelOption.TCP_NODELAY, true);
        result.option(ChannelOption.SO_LINGER,
                agentConfiguration.getProxyTcpSoLinger());
        result.option(ChannelOption.SO_RCVBUF,
                agentConfiguration.getProxyTcpSoRcvbuf());
        result.option(ChannelOption.SO_SNDBUF,
                agentConfiguration.getProxyTcpSoSndbuf());
        result.remoteAddress(agentConfiguration.getProxyHost(), agentConfiguration.getProxyPort());
        result.handler(new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel proxyChannel) {
                var proxyChannelPipeline = proxyChannel.pipeline();
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameDecoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                        0, ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER, 0,
                        ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
                proxyChannelPipeline.addLast(new ProxyMessageDecoder());
                proxyChannelPipeline.addLast(haProxyMessageBodyTypeHandler);
                proxyChannelPipeline.addLast(new HAExtractPureDataDecoder());
                proxyChannelPipeline.addLast(new HttpResponseDecoder());
                proxyChannelPipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE, true));
                proxyChannelPipeline.addLast(haSendPureDataToAgentHandler);
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameEncoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldPrepender(ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
                proxyChannelPipeline.addLast(new AgentMessageEncoder());
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
        result.option(ChannelOption.AUTO_CLOSE, false);
        result.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        result.option(ChannelOption.TCP_NODELAY, true);
        result.option(ChannelOption.SO_LINGER,
                agentConfiguration.getProxyTcpSoLinger());
        result.option(ChannelOption.SO_RCVBUF,
                agentConfiguration.getProxyTcpSoRcvbuf());
        result.option(ChannelOption.SO_SNDBUF,
                agentConfiguration.getProxyTcpSoSndbuf());
        result.remoteAddress(agentConfiguration.getProxyHost(), agentConfiguration.getProxyPort());
        result.handler(new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel proxyChannel) {
                var proxyChannelPipeline = proxyChannel.pipeline();
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameDecoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                        0, ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER, 0,
                        ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
                proxyChannelPipeline.addLast(new ProxyMessageDecoder());
                proxyChannelPipeline.addLast(haProxyMessageBodyTypeHandler);
                proxyChannelPipeline.addLast(new HAExtractPureDataDecoder());
                proxyChannelPipeline.addLast(haSendPureDataToAgentHandler);
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameEncoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldPrepender(ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
                proxyChannelPipeline.addLast(new AgentMessageEncoder());
                proxyChannelPipeline.addLast(PrintExceptionHandler.INSTANCE);
            }
        });
        return result;
    }
}
