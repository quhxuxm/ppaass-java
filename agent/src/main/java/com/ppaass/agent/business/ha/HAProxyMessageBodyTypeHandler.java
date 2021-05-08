package com.ppaass.agent.business.ha;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class HAProxyMessageBodyTypeHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final AgentConfiguration agentConfiguration;

    HAProxyMessageBodyTypeHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext proxyChannelContext, Throwable cause) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        logger.error(() -> "Proxy channel exception happen, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText(), cause});
        proxyChannel.close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext proxyChannelContext) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannel = proxyChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNEL).get();
        if (agentChannel == null) {
            return;
        }
        if (!proxyChannel.isActive()) {
            agentChannel.close();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannel = proxyChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNEL).get();
        if (agentChannel == null) {
            logger.error(
                    () -> "The agent channel id in proxy message is not for current proxy channel, discard the proxy message, proxy channel = {}, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyChannel.id().asLongText(),
                            proxyMessage
                    });
            return;
        }
        var connectionInfo = agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
        switch (proxyMessage.getBody().getBodyType()) {
            case TCP_CONNECT_FAIL -> {
                logger.error(
                        () -> "Connect fail for uri: [{}], close it, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                connectionInfo.getUri(), agentChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            }
            case TCP_CONNECT_SUCCESS -> {
                if (connectionInfo.isHttps()) {
                    //HTTPS
                    var okResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.valueOf(HttpResponseStatus.OK.code(),
                                            IHAConstant.CONNECTION_ESTABLISHED));
                    agentChannel.writeAndFlush(okResponse)
                            .addListener((ChannelFutureListener) agentWriteChannelFuture -> {
                                if (agentWriteChannelFuture.isSuccess()) {
                                    var agentChannelPipeline = agentChannel.pipeline();
                                    if (agentChannelPipeline.get(HttpServerCodec.class.getName()) != null) {
                                        agentChannelPipeline.remove(HttpServerCodec.class.getName());
                                    }
                                    if (agentChannelPipeline.get(
                                            HttpObjectAggregator.class.getName()) != null) {
                                        agentChannelPipeline.remove(
                                                HttpObjectAggregator.class.getName());
                                    }
                                    return;
                                }
                                logger.trace(
                                        () -> "Fail to write CONNECT_SUCCESS to client because of exception, uri=[{}] agent channel = {}, proxy channel = {}.",
                                        () -> new Object[]{
                                                connectionInfo.getUri(), agentChannel.id().asLongText(),
                                                proxyChannel.id().asLongText(),
                                                agentWriteChannelFuture.cause()
                                        });
                                var failResponse =
                                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                            });
                    return;
                }
                //HTTP
                HAUtil.INSTANCE.writeAgentMessageToProxy(
                        AgentMessageBodyType.TCP_DATA,
                        agentConfiguration.getUserToken(),
                        agentConfiguration.getAgentInstanceId(),
                        proxyChannel,
                        connectionInfo.getHttpMessageCarriedOnConnectTime(),
                        agentConfiguration.getAgentSourceAddress(),
                        agentConfiguration.getTcpPort(),
                        connectionInfo.getTargetHost(),
                        connectionInfo.getTargetPort(),
                        agentChannel.id().asLongText(),
                        proxyWriteChannelFuture -> {
                            if (proxyWriteChannelFuture.isSuccess()) {
                                return;
                            }
                            logger.trace(
                                    () -> "Fail to write HTTP DATA to from agent to proxy because of exception, uri=[{}] agent channel = {}, proxy channel = {}.",
                                    () -> new Object[]{
                                            connectionInfo.getUri(), agentChannel.id().asLongText(),
                                            proxyChannel.id().asLongText(),
                                            proxyWriteChannelFuture.cause()
                                    });
                            proxyChannel.close();
                            var failResponse =
                                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                        }
                );
            }
            case TCP_DATA_SUCCESS -> {
                proxyChannel.attr(IHAConstant.IProxyChannelConstant.AGENT_CHANNEL_TO_SEND_PURE_DATA).set(agentChannel);
                proxyChannelContext.fireChannelRead(proxyMessage);
            }
            case TCP_CONNECTION_CLOSE -> {
                agentChannel.close();
            }
            case TCP_DATA_FAIL -> {
                logger.trace(
                        () -> "FAIL_TCP happen close connection, agent channel = {}, proxy channel = {}.",
                        () -> new Object[]{
                                connectionInfo.getUri(), agentChannel.id().asLongText(),
                                proxyChannel.id().asLongText()
                        });
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            }
            case UDP_DATA_FAIL, UDP_DATA_SUCCESS -> {
                logger.trace(
                        () -> "No OK_UDP proxy message body type for HTTP agent, close it, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
                proxyChannel.close();
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }
}
