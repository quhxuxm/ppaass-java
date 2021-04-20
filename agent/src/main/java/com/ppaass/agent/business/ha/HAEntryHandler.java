package com.ppaass.agent.business.ha;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
@Service
public class HAEntryHandler extends SimpleChannelInboundHandler<Object> {
    private static final ScheduledExecutorService DELAY_CLOSE_EXECUTOR =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);
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
        PpaassLogger.INSTANCE.error(() -> "Exception happen on agent channel, close agent channel, agent channel = {}.",
                () -> new Object[]{
                        agentChannel.id().asLongText(), cause
                });
        agentChannel.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext agentChannelContext) {
        var agentChannel = agentChannelContext.channel();
        var connectionInfo = agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
        agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).set(null);
        if (connectionInfo == null) {
            PpaassLogger.INSTANCE
                    .debug(() -> "No connection info attached to agent channel, skip the step to return proxy channel, agent channel = {}",
                            () -> new Object[]{agentChannel.id().asLongText()});
            return;
        }
//        if (connectionInfo.isHttps()) {
//            PpaassLogger.INSTANCE
//                    .debug(() -> "Agent channel become inactive, but it is for HTTPS, so will not return the proxy channel, agent channel = {}",
//                            () -> new Object[]{agentChannel.id().asLongText()});
//            return;
//        }
        DELAY_CLOSE_EXECUTOR.schedule(() -> {
            var proxyChannel = connectionInfo.getProxyChannel();
            try {
                var channelPool =
                        proxyChannel.attr(IHAConstant.IProxyChannelConstant.CHANNEL_POOL)
                                .get();
                channelPool.returnObject(proxyChannel);
            } catch (Exception e) {
                PpaassLogger.INSTANCE
                        .debug(() -> "Fail to return proxy channel to pool because of exception, proxy channel = {}",
                                () -> new Object[]{
                                        proxyChannel.id().asLongText(), e
                                });
            }
        }, this.agentConfiguration.getDelayCloseTimeSeconds(), TimeUnit.SECONDS);
        PpaassLogger.INSTANCE
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
                    PpaassLogger.INSTANCE
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
                PpaassLogger.INSTANCE
                        .debug(HAEntryHandler.class,
                                () -> "A https CONNECT request send to uri: [{}], agent channel = {}",
                                () -> new Object[]{
                                        fullHttpRequest.uri(),
                                        agentChannel.id().asLongText()
                                });
                var httpsProxyTcpChannel =
                        this.haProxyResourceManager.getProxyTcpChannelPoolForHttps().borrowObject();
                connectionInfo.setAgentChannel(agentChannel);
                connectionInfo.setProxyChannel(httpsProxyTcpChannel);
                connectionInfo.setUserToken(agentConfiguration.getUserToken());
                connectionInfo.setOnConnecting(true);
                httpsProxyTcpChannel.attr(IHAConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO)
                        .set(connectionInfo);
                agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).set(connectionInfo);
                HAUtil.INSTANCE
                        .writeAgentMessageToProxy(AgentMessageBodyType.TCP_CONNECT, connectionInfo.getUserToken(),
                                agentConfiguration.getAgentInstanceId(), httpsProxyTcpChannel,
                                null, agentConfiguration.getAgentSourceAddress(), agentConfiguration.getTcpPort(),
                                connectionInfo.getTargetHost(), connectionInfo.getTargetPort(),
                                agentChannel.id().asLongText(),
                                proxyChannelWriteFuture -> {
                                    if (proxyChannelWriteFuture.isSuccess()) {
                                        return;
                                    }
                                    PpaassLogger.INSTANCE.error(
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
                PpaassLogger.INSTANCE.trace(
                        () -> "HTTP DATA send to uri: [{}], agent channel = {}, http data: \n{}\n",
                        () -> new Object[]{
                                fullHttpRequest.uri(),
                                agentChannel.id().asLongText(),
                                ByteBufUtil.prettyHexDump(fullHttpRequest.content())
                        });
                HAUtil.INSTANCE.writeAgentMessageToProxy(
                        AgentMessageBodyType.TCP_DATA,
                        connectionInfo.getUserToken(),
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
                            PpaassLogger.INSTANCE.error(
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
                PpaassLogger.INSTANCE.error(HAEntryHandler.class,
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
            PpaassLogger.INSTANCE.trace(HAEntryHandler.class,
                    () -> "A http FIRST request send to uri: [{}], agent channel = {}, http data:\n{}\n",
                    () -> new Object[]{
                            fullHttpRequest.uri(),
                            agentChannel.id().asLongText(),
                            ByteBufUtil.prettyHexDump(fullHttpRequest.content())
                    });
            var httpProxyTcpChannel = this.haProxyResourceManager.getProxyTcpChannelPoolForHttp().borrowObject();
            connectionInfo.setAgentChannel(agentChannel);
            connectionInfo.setProxyChannel(httpProxyTcpChannel);
            connectionInfo.setUserToken(agentConfiguration.getUserToken());
            connectionInfo.setHttpMessageCarriedOnConnectTime(fullHttpRequest);
            httpProxyTcpChannel.attr(IHAConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO).set(connectionInfo);
            agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).set(connectionInfo);
            HAUtil.INSTANCE
                    .writeAgentMessageToProxy(AgentMessageBodyType.TCP_CONNECT, connectionInfo.getUserToken(),
                            agentConfiguration.getAgentInstanceId(), httpProxyTcpChannel,
                            null, agentConfiguration.getAgentSourceAddress(),
                            agentConfiguration.getTcpPort(), connectionInfo.getTargetHost(),
                            connectionInfo.getTargetPort(),
                            agentChannel.id().asLongText(),
                            proxyChannelWriteFuture -> {
                                if (proxyChannelWriteFuture.isSuccess()) {
                                    return;
                                }
                                PpaassLogger.INSTANCE.error(
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
            PpaassLogger.INSTANCE.error(HAEntryHandler.class,
                    () -> "Close HTTPS agent channel because of connection info not existing for agent channel, agent channel = {}",
                    () -> new Object[]{
                            agentChannel.id().asLongText()
                    });
            var failResponse =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        connectionInfo.setOnConnecting(false);
        var httpsProxyTcpChannel = connectionInfo.getProxyChannel();
        PpaassLogger.INSTANCE.trace(HAEntryHandler.class,
                () -> "HTTPS DATA send to uri: [{}], agent channel = {}, https data:\n{}\n",
                () -> new Object[]{
                        connectionInfo.getUri(),
                        agentChannel.id().asLongText(),
                        ByteBufUtil.prettyHexDump((ByteBuf) httpProxyInput)
                });
        HAUtil.INSTANCE.writeAgentMessageToProxy(
                AgentMessageBodyType.TCP_DATA,
                connectionInfo.getUserToken(), agentConfiguration.getAgentInstanceId(),
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
                    PpaassLogger.INSTANCE.error(
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

