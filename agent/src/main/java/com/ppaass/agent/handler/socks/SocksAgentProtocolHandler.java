package com.ppaass.agent.handler.socks;

import com.ppaass.agent.AgentConfiguration;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class SocksAgentProtocolHandler extends SimpleChannelInboundHandler<SocksMessage> {
    private static final Logger logger = LoggerFactory.getLogger(SocksAgentProtocolHandler.class);
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
    protected void channelRead0(ChannelHandlerContext agentChannelContext, SocksMessage socksRequest) throws Exception {
        var agentChannel = agentChannelContext.channel();
        if (SocksVersion.UNKNOWN == socksRequest.version()) {
            logger.error(
                    "Incoming protocol is unknown protocol, agent channel = {}."
                    , agentChannel.id().asLongText());
            agentChannel.close();
            return;
        }
        if (SocksVersion.SOCKS4a == socksRequest.version()) {
            logger.error(
                    "Socks4a not support, agent channel = {}."
                    , agentChannel.id().asLongText());
            agentChannel.close();
            return;
        }
        logger.debug(
                "Incoming request socks5, agent channel = {}."
                , agentChannel.id().asLongText());
        var agentChannelPipeline = agentChannelContext.pipeline();
        if (socksRequest instanceof Socks5InitialRequest) {
            logger.debug(
                    "Socks5 initial request coming always NO_AUTH, agent channel = {}", agentChannel.id().asLongText());
            agentChannelPipeline.addFirst(new Socks5CommandRequestDecoder());
            agentChannelContext.writeAndFlush(
                    new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
            return;
        }
        if (!(socksRequest instanceof Socks5CommandRequest)) {
            logger.error("Wrong socks5 request, agent channel = {} ", agentChannel.id().asLongText());
            agentChannel.close();
            return;
        }
        var socks5CommandRequest = (Socks5CommandRequest) socksRequest;
        if (socks5CommandRequest.type() == Socks5CommandType.CONNECT) {
            this.socksProxyTcpBootstrap
                    .connect(this.agentConfiguration.getProxyHost(), this.agentConfiguration.getProxyPort())
                    .addListener(new SocksAgentTcpProxyChannelConnectListener());
            return;
        }
        if (socks5CommandRequest.type() == Socks5CommandType.UDP_ASSOCIATE) {
            agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
            this.socksProxyUdpBootstrap.bind(0).addListener(new SocksAgentUdpProxyChannelBindListener(agentChannel,
                    socksProxyTcpBootstrap, agentConfiguration, socks5CommandRequest));
            return;
        }
        logger.error("Wrong socks5 request, agent channel = {} ", agentChannel.id().asLongText());
        agentChannel.close();
    }
}
