package com.ppaass.agent.handler.http;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.handler.AgentMessageEncoder;
import com.ppaass.common.handler.PrintExceptionHandler;
import com.ppaass.common.handler.ProxyMessageDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.Lz4FrameDecoder;
import io.netty.handler.codec.compression.Lz4FrameEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HttpAgentConfigure {
    @Bean
    public Bootstrap proxyBootstrapForHttp(EventLoopGroup proxyTcpLoopGroup,
                                           HttpProxyToAgentHandler httpProxyToAgentHandler,
                                           PrintExceptionHandler printExceptionHandler,
                                           AgentConfiguration agentConfiguration,
                                           HttpProxyMessageBodyTypeHandler httpProxyMessageBodyTypeHandler) {
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
        result.handler(new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel proxyChannel) {
                var proxyChannelPipeline = proxyChannel.pipeline();
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameDecoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                        0, 4, 0,
                        4));
                proxyChannelPipeline.addLast(new ProxyMessageDecoder(
                        agentConfiguration.getAgentPrivateKey()));
                proxyChannelPipeline.addLast(httpProxyMessageBodyTypeHandler)
                proxyChannelPipeline.addLast(new HttpProxyMessageConvertToOriginalDataDecoder());
                proxyChannelPipeline.addLast(new HttpResponseDecoder());
                proxyChannelPipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE, true));
                proxyChannelPipeline.addLast(httpProxyToAgentHandler);
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameEncoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldPrepender(4));
                proxyChannelPipeline.addLast(new AgentMessageEncoder(
                        agentConfiguration.getProxyPublicKey()));
                proxyChannelPipeline.addLast(printExceptionHandler);
            }
        });
        return result;
    }

    @Bean
    public Bootstrap proxyBootstrapForHttps(EventLoopGroup proxyIoEventLoopGroup,
                                            HttpProxyToAgentHandler httpProxyToAgentHandler,
                                            PrintExceptionHandler printExceptionHandler,
                                            AgentConfiguration agentConfiguration,
                                            HttpProxyMessageBodyTypeHandler httpProxyMessageBodyTypeHandler) {
        Bootstrap result = new Bootstrap();
        result.group(proxyIoEventLoopGroup);
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
        result.handler(new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel proxyChannel) {
                var proxyChannelPipeline = proxyChannel.pipeline();
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameDecoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                        0, 4, 0,
                        4));
                proxyChannelPipeline.addLast(new ProxyMessageDecoder(
                        agentConfiguration.getAgentPrivateKey()));
                proxyChannelPipeline.addLast(httpProxyMessageBodyTypeHandler);
                proxyChannelPipeline.addLast(new HttpProxyMessageConvertToOriginalDataDecoder());
                proxyChannelPipeline.addLast(httpProxyToAgentHandler);
                if (agentConfiguration.isProxyTcpCompressEnable()) {
                    proxyChannelPipeline.addLast(new Lz4FrameEncoder());
                }
                proxyChannelPipeline.addLast(new LengthFieldPrepender(4));
                proxyChannelPipeline.addLast(new AgentMessageEncoder(
                        agentConfiguration.getProxyPublicKey()));
                proxyChannelPipeline.addLast(printExceptionHandler);
            }
        });
        return result;
    }
}
