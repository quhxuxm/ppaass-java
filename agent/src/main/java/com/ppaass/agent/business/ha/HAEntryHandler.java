package com.ppaass.agent.business.ha;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
import com.ppaass.agent.business.ChannelWrapper;
import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class HAEntryHandler extends SimpleChannelInboundHandler<Object> {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
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
        logger.error(() -> "Exception happen on agent channel, close agent channel, agent channel = {}.",
                () -> new Object[]{
                        agentChannel.id().asLongText(), cause
                });
        agentChannel.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext agentChannelContext) {
        var agentChannel = agentChannelContext.channel();
        var connectionInfo = agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            logger
                    .debug(() -> "No connection info attached to agent channel, skip the step to return proxy channel, agent channel = {}",
                            () -> new Object[]{agentChannel.id().asLongText()});
            return;
        }
        var proxyChannel = connectionInfo.getProxyChannel();
        try {
            var channelPool =
                    proxyChannel.attr(IHAConstant.IProxyChannelConstant.CHANNEL_POOL)
                            .get();
            channelPool.returnObject(proxyChannel);
        } catch (Exception e) {
            logger
                    .debug(() -> "Fail to return proxy channel to pool because of exception, proxy channel = {}",
                            () -> new Object[]{
                                    proxyChannel.id().asLongText(), e
                            });
        }
        var agentAgentChannels = proxyChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
        var agentAgentWrapper = agentAgentChannels.get(agentChannel.id().asLongText());
        if (agentAgentWrapper != null) {
            agentAgentWrapper.markClose();
        }
        logger
                .debug(() -> "Agent channel become inactive, and it is not for HTTPS, agent channel = {}",
                        () -> new Object[]{agentChannel.id().asLongText()});
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
                            .error(HAEntryHandler.class,
                                    () -> "Close agent channel because of fail to parse uri:[{}] on CONNECT, agent channel = {}",
                                    () -> new Object[]{
                                            fullHttpRequest.uri(),
                                            agentChannel.id().asLongText()
                                    });
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                logger
                        .debug(HAEntryHandler.class,
                                () -> "A https CONNECT request send to uri: [{}], agent channel = {}",
                                () -> new Object[]{
                                        fullHttpRequest.uri(),
                                        agentChannel.id().asLongText()
                                });
                Channel httpsProxyTcpChannel;
                try {
                    httpsProxyTcpChannel = this.haProxyResourceManager.getProxyTcpChannelPoolForHttps().borrowObject();
                } catch (Exception e) {
                    logger
                            .error(() -> "Fail to create proxy tcp channel connection because of exception, max connection number:{}, idle connection number:{}, active connection number:{}, agent channel = {}, target host={}, target port={}.",
                                    () -> new Object[]{
                                            this.haProxyResourceManager.getProxyTcpChannelPoolForHttps().getMaxTotal(),
                                            this.haProxyResourceManager.getProxyTcpChannelPoolForHttps().getNumIdle(),
                                            this.haProxyResourceManager.getProxyTcpChannelPoolForHttps().getNumActive(),
                                            agentChannel.id().asLongText(),
                                            connectionInfo.getTargetHost(),
                                            connectionInfo.getTargetPort(),
                                            e});
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    agentChannel.writeAndFlush(failResponse)
                            .addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                connectionInfo.setProxyChannel(httpsProxyTcpChannel);
                var agentChannelsOnProxyChannel =
                        httpsProxyTcpChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
                var agentChannelWrapper = new ChannelWrapper(agentChannel);
                agentChannelsOnProxyChannel.putIfAbsent(agentChannel.id().asLongText(), agentChannelWrapper);
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
                                            () -> "Fail to write HTTP/HTTPS connection data to proxy because of exception, agent channel = {}, proxy channel = {}.",
                                            () -> new Object[]{
                                                    agentChannel.id().asLongText(),
                                                    httpsProxyTcpChannel.id().asLongText(),
                                                    proxyChannelWriteFuture.cause()
                                            });
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
                        () -> "HTTP DATA send to uri: [{}], agent channel = {}, http data: \n{}\n",
                        () -> new Object[]{
                                fullHttpRequest.uri(),
                                agentChannel.id().asLongText(),
                                ByteBufUtil.prettyHexDump(fullHttpRequest.content())
                        });
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
                                    () -> "Fail to write HTTP data to uri:[{}] because of exception, agent channel = {}, proxy channel = {}.",
                                    () -> new Object[]{
                                            fullHttpRequest.uri(), agentChannel.id().asLongText(),
                                            proxyChannel.id().asLongText(),
                                            proxyChannelWriteFuture.cause()
                                    });
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
                logger.error(HAEntryHandler.class,
                        () -> "Close HTTP agent channel because of fail to parse uri:[{}], agent channel = {}",
                        () -> new Object[]{
                                fullHttpRequest.uri(),
                                agentChannel.id().asLongText()
                        });
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            logger.trace(HAEntryHandler.class,
                    () -> "A http FIRST request send to uri: [{}], agent channel = {}, http data:\n{}\n",
                    () -> new Object[]{
                            fullHttpRequest.uri(),
                            agentChannel.id().asLongText(),
                            ByteBufUtil.prettyHexDump(fullHttpRequest.content())
                    });
            Channel httpProxyTcpChannel;
            try {
                httpProxyTcpChannel = this.haProxyResourceManager.getProxyTcpChannelPoolForHttp().borrowObject();
            } catch (Exception e) {
                var finalConnectionInfo = connectionInfo;
                logger
                        .error(() -> "Fail to create proxy tcp channel connection because of exception, max connection number:{}, idle connection number:{}, active connection number:{}, agent channel = {},  target host={}, target port={}.",
                                () -> new Object[]{
                                        this.haProxyResourceManager.getProxyTcpChannelPoolForHttp().getMaxTotal(),
                                        this.haProxyResourceManager.getProxyTcpChannelPoolForHttp().getNumIdle(),
                                        this.haProxyResourceManager.getProxyTcpChannelPoolForHttp().getNumActive(),
                                        agentChannel.id().asLongText(),
                                        finalConnectionInfo.getTargetHost(),
                                        finalConnectionInfo.getTargetPort(),
                                        e});
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            connectionInfo.setProxyChannel(httpProxyTcpChannel);
            connectionInfo.setHttpMessageCarriedOnConnectTime(fullHttpRequest);
            var agentChannelsOnProxyChannel =
                    httpProxyTcpChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
            var agentChannelWrapper = new ChannelWrapper(agentChannel);
            agentChannelsOnProxyChannel.putIfAbsent(agentChannel.id().asLongText(), agentChannelWrapper);
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
                                        () -> "Fail to write HTTP/HTTPS connection data to proxy because of exception, agent channel = {}, proxy channel = {}.",
                                        () -> new Object[]{
                                                agentChannel.id().asLongText(), httpProxyTcpChannel.id().asLongText(),
                                                proxyChannelWriteFuture.cause()
                                        });
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
            logger.error(HAEntryHandler.class,
                    () -> "Close HTTPS agent channel because of connection info not existing for agent channel, agent channel = {}",
                    () -> new Object[]{
                            agentChannel.id().asLongText()
                    });
            var failResponse =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        var httpsProxyTcpChannel = connectionInfo.getProxyChannel();
        logger.trace(HAEntryHandler.class,
                () -> "HTTPS DATA send to uri: [{}], agent channel = {}, https data:\n{}\n",
                () -> new Object[]{
                        connectionInfo.getUri(),
                        agentChannel.id().asLongText(),
                        ByteBufUtil.prettyHexDump((ByteBuf) httpProxyInput)
                });
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
                            () -> "Fail to write HTTPS data to uri:[{}] because of exception, agent channel = {}, proxy channel = {}.",
                            () -> new Object[]{
                                    connectionInfo.getUri(),
                                    agentChannel.id().asLongText(), httpsProxyTcpChannel.id().asLongText(),
                                    proxyChannelWriteFuture.cause()
                            });
                    proxyChannelWriteFuture.channel().close();
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                });
    }
}

