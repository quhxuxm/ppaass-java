package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class SocksAgentProtocolHandler extends SimpleChannelInboundHandler<SocksMessage> {
    static {
        PpaassLogger.INSTANCE.register(SocksAgentProtocolHandler.class);
    }

    private final Bootstrap socksProxyTcpBootstrap;
    private final Bootstrap socksProxyUdpBootstrap;
    private final AgentConfiguration agentConfiguration;

    public SocksAgentProtocolHandler(Bootstrap socksProxyTcpBootstrap,
                                     Bootstrap socksProxyUdpBootstrap,
                                     AgentConfiguration agentConfiguration) {
        this.socksProxyTcpBootstrap = socksProxyTcpBootstrap;
        this.socksProxyUdpBootstrap = socksProxyUdpBootstrap;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void channelActive(ChannelHandlerContext agentChannelContext) throws Exception {
        super.channelActive(agentChannelContext);
        agentChannelContext.read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext agentChannelContext) throws Exception {
        super.channelReadComplete(agentChannelContext);
        agentChannelContext.read();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentChannelContext, SocksMessage socksRequest) throws Exception {
        var agentChannel = agentChannelContext.channel();
        if (SocksVersion.UNKNOWN == socksRequest.version()) {
            PpaassLogger.INSTANCE.error(SocksAgentProtocolHandler.class,
                    () -> "Incoming protocol is unknown protocol, agent channel = {}.", () -> new Object[]{
                            agentChannel.id().asLongText()
                    });
            agentChannel.close();
            return;
        }
        if (SocksVersion.SOCKS4a == socksRequest.version()) {
            PpaassLogger.INSTANCE
                    .error(SocksAgentProtocolHandler.class, () -> "Socks4a not support, agent channel = {}.",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            agentChannel.close();
            return;
        }
        var agentChannelPipeline = agentChannelContext.pipeline();
        if (socksRequest instanceof Socks5InitialRequest) {
            PpaassLogger.INSTANCE
                    .debug(SocksAgentProtocolHandler.class,
                            () -> "Socks5 initial request coming always NO_AUTH, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            agentChannelPipeline.addFirst(new Socks5CommandRequestDecoder());
            agentChannelContext.writeAndFlush(
                    new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                    .addListener((ChannelFutureListener) agentChannelFuture -> {
                        if (!agentChannelFuture.isSuccess()) {
                            agentChannel.close();
                            return;
                        }
                        agentChannelContext.read();
                    });
            return;
        }
        if (!(socksRequest instanceof Socks5CommandRequest)) {
            PpaassLogger.INSTANCE
                    .error(SocksAgentProtocolHandler.class,
                            () -> "Wrong socks5 request, agent channel = {} ",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            agentChannel.close();
            return;
        }
        var socks5CommandRequest = (Socks5CommandRequest) socksRequest;
        if (socks5CommandRequest.type() == Socks5CommandType.CONNECT) {
            PpaassLogger.INSTANCE
                    .debug(SocksAgentProtocolHandler.class,
                            () -> "Socks5 connect request coming, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            this.socksProxyTcpBootstrap
                    .connect(this.agentConfiguration.getProxyHost(), this.agentConfiguration.getProxyPort())
                    .addListener(new SocksAgentA2PConnectListener(agentChannel, socks5CommandRequest,
                            agentConfiguration));
            return;
        }
        if (socks5CommandRequest.type() == Socks5CommandType.UDP_ASSOCIATE) {
            PpaassLogger.INSTANCE
                    .debug(SocksAgentProtocolHandler.class,
                            () -> "Socks5 udp associate request coming, agent channel = {}",
                            () -> new Object[]{
                                    agentChannel.id().asLongText()
                            });
            agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
            this.socksProxyUdpBootstrap.bind(0).addListener(new SocksAgentProxyUdpChannelBindListener(agentChannel,
                    socksProxyTcpBootstrap, agentConfiguration, socks5CommandRequest));
            return;
        }
        PpaassLogger.INSTANCE
                .error(SocksAgentProtocolHandler.class,
                        () -> "Wrong socks5 request, agent channel = {} ",
                        () -> new Object[]{
                                agentChannel.id().asLongText()
                        });
        agentChannel.close();
    }
}
