package com.ppaass.agent.business.http;

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
import org.apache.commons.pool2.DestroyMode;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class HttpAgentProtocolHandler extends SimpleChannelInboundHandler<Object> {
    private final AgentConfiguration agentConfiguration;
    private final HttpAgentProxyResourceManager httpAgentProxyResourceManager;

    public HttpAgentProtocolHandler(AgentConfiguration agentConfiguration,
                                    HttpAgentProxyResourceManager httpAgentProxyResourceManager) {
        this.agentConfiguration = agentConfiguration;
        this.httpAgentProxyResourceManager = httpAgentProxyResourceManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentChannelContext, Object httpProxyInput) throws Exception {
        var agentChannel = agentChannelContext.channel();
        if (httpProxyInput instanceof FullHttpRequest) {
            var fullHttpRequest = (FullHttpRequest) httpProxyInput;
            var connectionHeader = fullHttpRequest.headers().get(HttpHeaderNames.CONNECTION);
            var connectionKeepAlive =
                    HttpHeaderValues.KEEP_ALIVE.contentEqualsIgnoreCase(connectionHeader);
            if (HttpMethod.CONNECT == fullHttpRequest.method()) {
                //A HTTPS request to setup the connection
                var connectionInfo =
                        HttpAgentUtil.INSTANCE.parseConnectionInfo(fullHttpRequest.uri());
                if (connectionInfo == null) {
                    PpaassLogger.INSTANCE
                            .error(HttpAgentProtocolHandler.class,
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
                        .debug(HttpAgentProtocolHandler.class,
                                () -> "A https CONNECT request send to uri: [{}], agent channel = {}",
                                () -> new Object[]{
                                        fullHttpRequest.uri(),
                                        agentChannel.id().asLongText()
                                });
                connectionInfo.setKeepAlive(connectionKeepAlive);
                var httpsProxyTcpChannel =
                        this.httpAgentProxyResourceManager.getProxyTcpChannelPoolForHttps().borrowObject();
                connectionInfo.setAgentChannel(agentChannel);
                connectionInfo.setProxyChannel(httpsProxyTcpChannel);
                connectionInfo.setUserToken(agentConfiguration.getUserToken());
                httpsProxyTcpChannel.attr(IHttpAgentConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO)
                        .set(connectionInfo);
                agentChannel.attr(IHttpAgentConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).set(connectionInfo);
                HttpAgentUtil.INSTANCE
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
                                    httpsProxyTcpChannel.close().addListener(future -> {
                                        var failResponse =
                                                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                        HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                        agentChannel.writeAndFlush(failResponse)
                                                .addListener(ChannelFutureListener.CLOSE);
                                    });
                                });
                return;
            }
            // A HTTP request
            ReferenceCountUtil.retain(httpProxyInput, 1);
            var connectionInfo = agentChannel.attr(IHttpAgentConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
            if (connectionInfo != null) {
                var proxyChannel = connectionInfo.getProxyChannel();
                PpaassLogger.INSTANCE.trace(
                        () -> "HTTP DATA send to uri: [{}], agent channel = {}, http data: \n{}\n",
                        () -> new Object[]{
                                fullHttpRequest.uri(),
                                agentChannel.id().asLongText(),
                                ByteBufUtil.prettyHexDump(fullHttpRequest.content())
                        });
                HttpAgentUtil.INSTANCE.writeAgentMessageToProxy(
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
                            var channelPool =
                                    proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL).get();
                            channelPool.invalidateObject(proxyChannel, DestroyMode.ABANDONED);
                            var failResponse =
                                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                        }
                );
                return;
            }
            //First time create HTTP connection
            connectionInfo = HttpAgentUtil.INSTANCE.parseConnectionInfo(fullHttpRequest.uri());
            if (connectionInfo == null) {
                PpaassLogger.INSTANCE.error(HttpAgentProtocolHandler.class,
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
            connectionInfo.setKeepAlive(connectionKeepAlive);
            PpaassLogger.INSTANCE.trace(HttpAgentProtocolHandler.class,
                    () -> "A http FIRST request send to uri: [{}], agent channel = {}, http data:\n{}\n",
                    () -> new Object[]{
                            fullHttpRequest.uri(),
                            agentChannel.id().asLongText(),
                            ByteBufUtil.prettyHexDump(fullHttpRequest.content())
                    });
            var httpProxyTcpChannel = this.httpAgentProxyResourceManager.getProxyTcpChannelPoolForHttp().borrowObject();
            connectionInfo.setAgentChannel(agentChannel);
            connectionInfo.setProxyChannel(httpProxyTcpChannel);
            connectionInfo.setUserToken(agentConfiguration.getUserToken());
            connectionInfo.setHttpMessageCarriedOnConnectTime(fullHttpRequest);
            httpProxyTcpChannel.attr(IHttpAgentConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO).set(connectionInfo);
            agentChannel.attr(IHttpAgentConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).set(connectionInfo);
            HttpAgentUtil.INSTANCE
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
                                var channelPool =
                                        httpProxyTcpChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                                                .get();
                                channelPool.invalidateObject(httpProxyTcpChannel, DestroyMode.ABANDONED);
                                var failResponse =
                                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                            });
            return;
        }
        //A HTTPS request to send data
        var connectionInfo = agentChannel.attr(IHttpAgentConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            PpaassLogger.INSTANCE.error(HttpAgentProtocolHandler.class,
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
        PpaassLogger.INSTANCE.trace(HttpAgentProtocolHandler.class,
                () -> "HTTPS DATA send to uri: [{}], agent channel = {}, https data:\n{}\n",
                () -> new Object[]{
                        connectionInfo.getUri(),
                        agentChannel.id().asLongText(),
                        ByteBufUtil.prettyHexDump((ByteBuf) httpProxyInput)
                });
        HttpAgentUtil.INSTANCE.writeAgentMessageToProxy(
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
                    var channelPool =
                            httpsProxyTcpChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL).get();
                    channelPool.invalidateObject(httpsProxyTcpChannel, DestroyMode.ABANDONED);
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                });
    }
}

