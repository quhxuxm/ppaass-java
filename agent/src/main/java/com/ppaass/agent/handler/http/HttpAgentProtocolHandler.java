package com.ppaass.agent.handler.http;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.message.AgentMessageBodyType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class HttpAgentProtocolHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = LoggerFactory.getLogger(HttpAgentProtocolHandler.class);
    private final AgentConfiguration agentConfiguration;
    private final Bootstrap proxyBootstrapForHttp;
    private final Bootstrap proxyBootstrapForHttps;

    public HttpAgentProtocolHandler(AgentConfiguration agentConfiguration,
                                    Bootstrap proxyBootstrapForHttp,
                                    Bootstrap proxyBootstrapForHttps) {
        this.agentConfiguration = agentConfiguration;
        this.proxyBootstrapForHttp = proxyBootstrapForHttp;
        this.proxyBootstrapForHttps = proxyBootstrapForHttps;
    }

    @Override
    public void channelActive(ChannelHandlerContext agentChannelContext) throws Exception {
        super.channelActive(agentChannelContext);
        agentChannelContext.read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext agentChannelContext) throws Exception {
        super.channelReadComplete(agentChannelContext);
        var agentChannel = agentChannelContext.channel();
        var connectionInfo = agentChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo != null) {
            var proxyChannel = connectionInfo.getProxyChannel();
            if (proxyChannel.isWritable()) {
                agentChannel.read();
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext agentChannelContext, Object httpProxyInput) throws Exception {
        var agentChannel = agentChannelContext.channel();
        if (httpProxyInput instanceof FullHttpRequest) {
            var fullHttpRequest = (FullHttpRequest) httpProxyInput;
            var connectionHeader = fullHttpRequest.headers().get(HttpHeaderNames.PROXY_CONNECTION);
            if (connectionHeader == null) {
                connectionHeader = fullHttpRequest.headers().get(HttpHeaderNames.CONNECTION);
            }
            var connectionKeepAlive =
                    HttpHeaderValues.KEEP_ALIVE.contentEqualsIgnoreCase(connectionHeader);
            if (HttpMethod.CONNECT == fullHttpRequest.method()) {
                //A HTTPS request to setup the connection
                logger.debug("A https CONNECT request send to uri: {}, agent channel = {}", fullHttpRequest.uri(), agentChannel.id().asLongText());
                var connectionInfo = HttpAgentUtil.INSTANCE.parseConnectionInfo(fullHttpRequest.uri());
                if (connectionInfo == null) {
                    agentChannel.close();
                    return;
                }
                connectionInfo.setKeepAlive(connectionKeepAlive);
                this.proxyBootstrapForHttps.connect(agentConfiguration.getProxyHost(),
                        agentConfiguration.getProxyPort())
                        .addListener(
                                new HttpAgentProxyConnectListener(agentChannel, connectionInfo, agentConfiguration,
                                        null));
                return;
            }
            // A HTTP request
            ReferenceCountUtil.retain(httpProxyInput, 1);
            var connectionInfo = agentChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).get();
            if (connectionInfo != null) {
                var proxyChannel = connectionInfo.getProxyChannel();
                logger.debug("HTTP DATA send to uri: {}, agent channel = {}", fullHttpRequest.uri(), agentChannel.id().asLongText());
                HttpAgentUtil.INSTANCE.writeAgentMessageToProxy(
                        AgentMessageBodyType.TCP_DATA,
                        connectionInfo.getUserToken(),
                        connectionInfo.getProxyChannel(),
                        httpProxyInput,
                        connectionInfo.getTargetHost(),
                        connectionInfo.getTargetPort(),
                        proxyChannelWriteFuture -> {
                            if (proxyChannelWriteFuture.isSuccess()) {
                                agentChannel.read();
                                return;
                            }
                            logger.error(
                                    "Fail to write HTTP data to proxy because of exception, agent channel = {}, proxy channel = {}.",
                                    agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                                    proxyChannelWriteFuture.cause());
                            agentChannel.close();
                            proxyChannel.close();
                        }
                );
                return;
            }
            //First time create HTTP connection
            connectionInfo = HttpAgentUtil.INSTANCE.parseConnectionInfo(fullHttpRequest.uri());
            if (connectionInfo == null) {
                agentChannel.close();
                return;
            }
            connectionInfo.setKeepAlive(connectionKeepAlive);
            logger.debug("A http FIRST request send to uri: {}, agent channel = {}", fullHttpRequest.uri(), agentChannel.id().asLongText());
            this.proxyBootstrapForHttp.connect(agentConfiguration.getProxyHost(),
                    agentConfiguration.getProxyPort())
                    .addListener(new HttpAgentProxyConnectListener(agentChannel, connectionInfo, agentConfiguration,
                            fullHttpRequest));
            return;
        }
        //A HTTPS request to send data
        var connectionInfo = agentChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            agentChannel.close();
            return;
        }
        var proxyChannel = connectionInfo.getProxyChannel();
        logger.debug("HTTPS DATA send to uri: {}, agent channel = {}", connectionInfo.getUri(), agentChannel.id().asLongText());
        HttpAgentUtil.INSTANCE.writeAgentMessageToProxy(
                AgentMessageBodyType.TCP_DATA,
                connectionInfo.getUserToken(),
                connectionInfo.getProxyChannel(),
                httpProxyInput,
                connectionInfo.getTargetHost(),
                connectionInfo.getTargetPort(),
                proxyChannelWriteFuture -> {
                    if (proxyChannelWriteFuture.isSuccess()) {
                        agentChannel.read();
                        return;
                    }
                    logger.error(
                            "Fail to write HTTPS data to proxy because of exception, agent channel = {}, proxy channel = {}.",
                            agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                            proxyChannelWriteFuture.cause());
                    agentChannel.close();
                    proxyChannel.close();
                });
    }
}

