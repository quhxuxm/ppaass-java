package com.ppaass.proxy;

import com.ppaass.common.constant.ICommonConstant;
import com.ppaass.common.handler.AgentMessageDecoder;
import com.ppaass.common.handler.PrintExceptionHandler;
import com.ppaass.common.handler.ProxyMessageEncoder;
import com.ppaass.proxy.handler.CleanupInactiveProxyChannelHandler;
import com.ppaass.proxy.handler.ProxyEntryChannelHandler;
import com.ppaass.proxy.handler.ReceiveTargetTcpDataChannelHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.Lz4FrameDecoder;
import io.netty.handler.codec.compression.Lz4FrameEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.ppaass.proxy.IProxyConstant.LAST_INBOUND_HANDLER;

@Configuration
class ProxyConfigure {
    private final ProxyConfiguration proxyConfiguration;

    ProxyConfigure(ProxyConfiguration proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
    }

    @Bean
    public NioEventLoopGroup proxyTcpMasterLoopGroup() {
        return new NioEventLoopGroup(
                this.proxyConfiguration.getProxyTcpMasterThreadNumber());
    }

    @Bean
    public NioEventLoopGroup proxyTcpWorkerLoopGroup() {
        return new NioEventLoopGroup(
                this.proxyConfiguration.getProxyTcpWorkerThreadNumber());
    }

    @Bean
    public NioEventLoopGroup targetTcpLoopGroup() {
        return new NioEventLoopGroup(
                this.proxyConfiguration.getTargetTcpThreadNumber());
    }

    @Bean
    public Bootstrap targetTcpBootstrap(
            EventLoopGroup targetTcpLoopGroup,
            ReceiveTargetTcpDataChannelHandler receiveTargetTcpDataChannelHandler) {
        Bootstrap result = new Bootstrap();
        result.group(targetTcpLoopGroup);
        result.channel(NioSocketChannel.class);
        result.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                proxyConfiguration.getTargetTcpConnectionTimeout());
        result.option(ChannelOption.SO_KEEPALIVE, true);
        result.option(ChannelOption.TCP_NODELAY, true);
        result.option(ChannelOption.SO_REUSEADDR, true);
        result.option(ChannelOption.AUTO_READ, true);
        result.option(ChannelOption.AUTO_CLOSE, true);
        result.option(ChannelOption.SO_LINGER, proxyConfiguration.getTargetTcpSoLinger());
        result.option(ChannelOption.SO_RCVBUF, proxyConfiguration.getTargetTcpSoRcvbuf());
        result.option(ChannelOption.SO_SNDBUF, proxyConfiguration.getTargetTcpSoSndbuf());
        result.option(ChannelOption.WRITE_SPIN_COUNT,
                proxyConfiguration.getTargetTcpWriteSpinCount());
        result.option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                new WriteBufferWaterMark(proxyConfiguration.getTargetTcpWriteBufferWaterMarkLow(),
                        proxyConfiguration.getTargetTcpWriteBufferWaterMarkHigh()));
        result.option(ChannelOption.RCVBUF_ALLOCATOR,
                new AdaptiveRecvByteBufAllocator(
                        proxyConfiguration.getTargetTcpReceiveDataAverageBufferMinSize(),
                        proxyConfiguration
                                .getTargetTcpReceiveDataAverageBufferInitialSize(),
                        proxyConfiguration.getTargetTcpReceiveDataAverageBufferMaxSize()));
        var channelInitializer = new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel targetChannel) {
                targetChannel.pipeline().addLast(new ChannelTrafficShapingHandler(
                        proxyConfiguration.getTargetTcpTrafficShapingWriteChannelLimit(),
                        proxyConfiguration.getTargetTcpTrafficShapingReadChannelLimit(),
                        proxyConfiguration.getTargetTcpTrafficShapingCheckInterval()
                ));
                targetChannel.pipeline().addLast(receiveTargetTcpDataChannelHandler);
                targetChannel.pipeline().addLast(LAST_INBOUND_HANDLER, PrintExceptionHandler.INSTANCE);
            }
        };
        result.handler(channelInitializer);
        return result;
    }

    @Bean
    public ServerBootstrap proxyTcpServerBootstrap(EventLoopGroup proxyTcpMasterLoopGroup,
                                                   EventLoopGroup proxyTcpWorkerLoopGroup,
                                                   CleanupInactiveProxyChannelHandler cleanupInactiveProxyChannelHandler,
                                                   ProxyEntryChannelHandler proxyEntryChannelHandler) {
        ServerBootstrap result = new ServerBootstrap();
        result.group(proxyTcpMasterLoopGroup, proxyTcpWorkerLoopGroup);
        result.channel(NioServerSocketChannel.class);
        result.option(ChannelOption.SO_BACKLOG, proxyConfiguration.getProxyTcpSoBacklog());
        result.option(ChannelOption.TCP_NODELAY, true);
        result.childOption(ChannelOption.TCP_NODELAY, true);
        result.childOption(ChannelOption.SO_REUSEADDR, true);
        result.childOption(ChannelOption.TCP_NODELAY, true);
        result.childOption(ChannelOption.AUTO_CLOSE, false);
        result.childOption(ChannelOption.AUTO_READ, true);
        result.childOption(ChannelOption.SO_KEEPALIVE, true);
        result.childOption(ChannelOption.SO_LINGER,
                proxyConfiguration.getProxyTcpSoLinger());
        result.childOption(ChannelOption.SO_RCVBUF, proxyConfiguration.getProxyTcpSoRcvbuf());
        result.childOption(ChannelOption.SO_SNDBUF, proxyConfiguration.getProxyTcpSoSndbuf());
        result.childOption(ChannelOption.WRITE_SPIN_COUNT,
                proxyConfiguration.getProxyTcpWriteSpinCount());
        result.childOption(ChannelOption.RCVBUF_ALLOCATOR,
                new AdaptiveRecvByteBufAllocator(
                        proxyConfiguration.getProxyTcpReceiveDataAverageBufferMinSize(),
                        proxyConfiguration.getProxyTcpReceiveDataAverageBufferInitialSize(),
                        proxyConfiguration.getProxyTcpReceiveDataAverageBufferMaxSize()));
        var channelInitializer = new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel proxyChannel) {
                //Inbound
                proxyChannel.pipeline().addLast(
                        new IdleStateHandler(0,
                                0,
                                proxyConfiguration.getProxyTcpChannelAllIdleSeconds()));
                proxyChannel.pipeline().addLast(cleanupInactiveProxyChannelHandler);
                proxyChannel.pipeline().addLast(new ChannelTrafficShapingHandler(
                        proxyConfiguration.getProxyTcpTrafficShapingWriteChannelLimit(),
                        proxyConfiguration.getProxyTcpTrafficShapingReadChannelLimit(),
                        proxyConfiguration.getProxyTcpTrafficShapingCheckInterval()
                ));
                if (proxyConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannel.pipeline().addLast(new Lz4FrameDecoder());
                }
                proxyChannel.pipeline().addLast(
                        new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0,
                                ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER, 0,
                                ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
                proxyChannel.pipeline().addLast(
                        new AgentMessageDecoder(proxyConfiguration.getProxyPrivateKey()));
                proxyChannel.pipeline().addLast(proxyEntryChannelHandler);
                if (proxyConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannel.pipeline().addLast(new Lz4FrameEncoder());
                }
                //Outbound
                proxyChannel.pipeline()
                        .addLast(new LengthFieldPrepender(ICommonConstant.LENGTH_FRAME_FIELD_BYTE_NUMBER));
                proxyChannel.pipeline().addLast(
                        new ProxyMessageEncoder(proxyConfiguration.getAgentPublicKey()));
                proxyChannel.pipeline().addLast(LAST_INBOUND_HANDLER, PrintExceptionHandler.INSTANCE);
            }
        };
        result.childHandler(channelInitializer);
        return result;
    }
}
