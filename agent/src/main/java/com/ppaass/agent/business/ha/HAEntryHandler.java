package com.ppaass.agent.business.ha;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class HAEntryHandler extends SimpleChannelInboundHandler<Object> {
    private final Logger logger = LoggerFactory.getLogger(HAEntryHandler.class);
    private final AgentConfiguration agentConfiguration;
    private final HAProxyResourceManager haProxyResourceManager;

    public HAEntryHandler(AgentConfiguration agentConfiguration,
                          HAProxyResourceManager haProxyResourceManager) {
        this.agentConfiguration = agentConfiguration;
        this.haProxyResourceManager = haProxyResourceManager;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext agentChannelContext, Throwable cause) {
        var agentChannel = agentChannelContext.channel();
        logger.error("Exception happen on agent channel, close agent channel, agent channel = {}.",
                agentChannel.id().asLongText(), cause
        );
        agentChannel.close();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext agentChannelContext) throws Exception {
        var agentChannel = agentChannelContext.channel();
        var haConnectionInfo = agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
        if (haConnectionInfo == null) {
            return;
        }
        if (haConnectionInfo.getProxyChannel().isActive()) {
            haConnectionInfo.getProxyChannel().close();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentChannelContext, Object httpProxyInput) throws Exception {
        var agentChannel = agentChannelContext.channel();
        if (httpProxyInput instanceof FullHttpRequest) {
            var fullHttpRequest = (FullHttpRequest) httpProxyInput;
            if (HttpMethod.CONNECT == fullHttpRequest.method()) {
                //A HTTPS request to setup the connection
                var connectionInfo =
                        HAUtil.INSTANCE.parseConnectionInfo(fullHttpRequest.uri());
                if (connectionInfo == null) {
                    logger
                            .error(
                                    "Close agent channel because of fail to parse uri:[{}] on CONNECT, agent channel = {}",
                                    fullHttpRequest.uri(),
                                    agentChannel.id().asLongText()
                            );
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                logger
                        .debug(
                                "A https CONNECT request send to uri: [{}], agent channel = {}",
                                fullHttpRequest.uri(),
                                agentChannel.id().asLongText()
                        );
                Channel httpsProxyTcpChannel;
                try {
                    httpsProxyTcpChannel =
                            this.haProxyResourceManager.getProxyTcpChannelBootstrapForHttps().connect().sync()
                                    .channel();
                } catch (Exception e) {
                    logger
                            .error("Fail to create proxy tcp channel connection because of exception, agent channel = {}, target host={}, target port={}.",
                                    agentChannel.id().asLongText(),
                                    connectionInfo.getTargetHost(),
                                    connectionInfo.getTargetPort(),
                                    e);
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    agentChannel.writeAndFlush(failResponse)
                            .addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                connectionInfo.setProxyChannel(httpsProxyTcpChannel);
                httpsProxyTcpChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNEL).set(agentChannel);
                agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).set(connectionInfo);
                HAUtil.INSTANCE
                        .writeAgentMessageToProxy(AgentMessageBodyType.TCP_CONNECT, agentConfiguration.getUserToken(),
                                agentConfiguration.getAgentInstanceId(), httpsProxyTcpChannel,
                                null, agentConfiguration.getAgentSourceAddress(), agentConfiguration.getTcpPort(),
                                connectionInfo.getTargetHost(), connectionInfo.getTargetPort(),
                                agentChannel.id().asLongText(),
                                proxyChannelWriteFuture -> {
                                    if (proxyChannelWriteFuture.isSuccess()) {
                                        return;
                                    }
                                    logger.error(
                                            "Fail to write HTTP/HTTPS connection data to proxy because of exception, agent channel = {}, proxy channel = {}.",
                                            agentChannel.id().asLongText(),
                                            httpsProxyTcpChannel.id().asLongText(),
                                            proxyChannelWriteFuture.cause()
                                    );
                                    httpsProxyTcpChannel.close();
                                    var failResponse =
                                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                    agentChannel.writeAndFlush(failResponse)
                                            .addListener(ChannelFutureListener.CLOSE);
                                });
                return;
            }
            // A HTTP request
            ReferenceCountUtil.retain(httpProxyInput, 1);
            var connectionInfo = agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
            if (connectionInfo != null) {
                var proxyChannel = connectionInfo.getProxyChannel();
                logger.trace(
                        "HTTP DATA send to uri: [{}], agent channel = {}, http data: \n{}\n",
                        fullHttpRequest.uri(),
                        agentChannel.id().asLongText(),
                        ByteBufUtil.prettyHexDump(fullHttpRequest.content())
                );
                HAUtil.INSTANCE.writeAgentMessageToProxy(
                        AgentMessageBodyType.TCP_DATA,
                        agentConfiguration.getUserToken(),
                        agentConfiguration.getAgentInstanceId(),
                        connectionInfo.getProxyChannel(),
                        httpProxyInput,
                        agentConfiguration.getAgentSourceAddress(),
                        agentConfiguration.getTcpPort(),
                        connectionInfo.getTargetHost(),
                        connectionInfo.getTargetPort(),
                        agentChannel.id().asLongText(),
                        proxyChannelWriteFuture -> {
                            if (proxyChannelWriteFuture.isSuccess()) {
                                return;
                            }
                            logger.error(
                                    "Fail to write HTTP data to uri:[{}] because of exception, agent channel = {}, proxy channel = {}.",
                                    fullHttpRequest.uri(), agentChannel.id().asLongText(),
                                    proxyChannel.id().asLongText(),
                                    proxyChannelWriteFuture.cause()
                            );
                            proxyChannel.close();
                            var failResponse =
                                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                        }
                );
                return;
            }
            //First time create HTTP connection
            connectionInfo = HAUtil.INSTANCE.parseConnectionInfo(fullHttpRequest.uri());
            if (connectionInfo == null) {
                logger.error(
                        "Close HTTP agent channel because of fail to parse uri:[{}], agent channel = {}",
                        fullHttpRequest.uri(),
                        agentChannel.id().asLongText()
                );
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            logger.trace(
                    "A http FIRST request send to uri: [{}], agent channel = {}, http data:\n{}\n",
                    fullHttpRequest.uri(),
                    agentChannel.id().asLongText(),
                    ByteBufUtil.prettyHexDump(fullHttpRequest.content())
            );
            Channel httpProxyTcpChannel;
            try {
                httpProxyTcpChannel =
                        this.haProxyResourceManager.getProxyTcpChannelBootstrapForHttp().connect().sync().channel();
            } catch (Exception e) {
                var finalConnectionInfo = connectionInfo;
                logger
                        .error("Fail to create proxy tcp channel connection because of exception, agent channel = {},  target host={}, target port={}.",
                                agentChannel.id().asLongText(),
                                finalConnectionInfo.getTargetHost(),
                                finalConnectionInfo.getTargetPort(),
                                e);
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            connectionInfo.setProxyChannel(httpProxyTcpChannel);
            connectionInfo.setHttpMessageCarriedOnConnectTime(fullHttpRequest);
            httpProxyTcpChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNEL).set(agentChannel);
            agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).set(connectionInfo);
            HAUtil.INSTANCE
                    .writeAgentMessageToProxy(AgentMessageBodyType.TCP_CONNECT, agentConfiguration.getUserToken(),
                            agentConfiguration.getAgentInstanceId(), httpProxyTcpChannel,
                            null, agentConfiguration.getAgentSourceAddress(),
                            agentConfiguration.getTcpPort(), connectionInfo.getTargetHost(),
                            connectionInfo.getTargetPort(),
                            agentChannel.id().asLongText(),
                            proxyChannelWriteFuture -> {
                                if (proxyChannelWriteFuture.isSuccess()) {
                                    return;
                                }
                                logger.error(
                                        "Fail to write HTTP/HTTPS connection data to proxy because of exception, agent channel = {}, proxy channel = {}.",
                                        agentChannel.id().asLongText(), httpProxyTcpChannel.id().asLongText(),
                                        proxyChannelWriteFuture.cause()
                                );
                                httpProxyTcpChannel.close();
                                var failResponse =
                                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                            });
            return;
        }
        //A HTTPS request to send data
        var connectionInfo = agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            logger.error(
                    "Close HTTPS agent channel because of connection info not existing for agent channel, agent channel = {}",
                    agentChannel.id().asLongText()
            );
            var failResponse =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        var httpsProxyTcpChannel = connectionInfo.getProxyChannel();
        logger.trace(
                "HTTPS DATA send to uri: [{}], agent channel = {}, https data:\n{}\n",
                connectionInfo.getUri(),
                agentChannel.id().asLongText(),
                ByteBufUtil.prettyHexDump((ByteBuf) httpProxyInput)
        );
        HAUtil.INSTANCE.writeAgentMessageToProxy(
                AgentMessageBodyType.TCP_DATA,
                agentConfiguration.getUserToken(), agentConfiguration.getAgentInstanceId(),
                connectionInfo.getProxyChannel(),
                httpProxyInput,
                agentConfiguration.getAgentSourceAddress(),
                agentConfiguration.getTcpPort(),
                connectionInfo.getTargetHost(),
                connectionInfo.getTargetPort(),
                agentChannel.id().asLongText(),
                proxyChannelWriteFuture -> {
                    if (proxyChannelWriteFuture.isSuccess()) {
                        return;
                    }
                    logger.error(
                            "Fail to write HTTPS data to uri:[{}] because of exception, agent channel = {}, proxy channel = {}.",
                            connectionInfo.getUri(),
                            agentChannel.id().asLongText(), httpsProxyTcpChannel.id().asLongText(),
                            proxyChannelWriteFuture.cause()
                    );
                    proxyChannelWriteFuture.channel().close();
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                });
    }
}

