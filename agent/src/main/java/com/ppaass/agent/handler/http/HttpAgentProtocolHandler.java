package com.ppaass.agent.handler.http;

import com.ppaass.agent.AgentConfiguration;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class HttpAgentProtocolHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = LoggerFactory.getLogger(HttpAgentProtocolHandler.class);
    private AgentConfiguration agentConfiguration;
    private Bootstrap proxyBootstrapForHttp;
    private Bootstrap proxyBootstrapForHttps;

    public HttpAgentProtocolHandler(AgentConfiguration agentConfiguration,
                                    Bootstrap proxyBootstrapForHttp,
                                    Bootstrap proxyBootstrapForHttps) {
        this.agentConfiguration = agentConfiguration;
        this.proxyBootstrapForHttp = proxyBootstrapForHttp;
        this.proxyBootstrapForHttps = proxyBootstrapForHttps;
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
                //A https request to setup the connection
                var connectionInfo = HttpAgentUtil.INSTANCE.parseConnectionInfo(fullHttpRequest.uri());
                if (connectionInfo == null) {
                    agentChannel.close();
                    return;
                }
                connectionInfo.setKeepAlive(connectionKeepAlive);
                this.proxyBootstrapForHttps.connect(agentConfiguration.getProxyHost(),
                        agentConfiguration.getProxyPort())
                        .addListener((ChannelFutureListener) proxyChannelFuture -> {
                            if(!proxyChannelFuture.isSuccess()){
                                agentChannel.close();
                                return;
                            }
                            var proxyChannel = proxyChannelFuture.channel();
                            connectionInfo.setAgentChannel( agentChannel);
                            connectionInfo.setProxyChannel( proxyChannel);
                            connectionInfo.setUserToken(agentConfiguration.getUserToken());
                            connectionInfo.setHttpMessageCarriedOnConnectTime( null);
                            proxyChannel.attr(HTTP_CONNECTION_INFO).setIfAbsent(connectionInfo)
                            agentChannel.attr(HTTP_CONNECTION_INFO).setIfAbsent(connectionInfo)
                            val bodyType = if (connectionInfo.isKeepAlive) {
                                proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
                                agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
                                AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE
                            } else {
                                proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
                                agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
                                AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE
                            }
                            writeAgentMessageToProxy(
                                    bodyType = bodyType,
                                    userToken = connectionInfo.userToken!!,
                                    proxyChannel = proxyChannel,
                                    input = null,
                                    targetHost = connectionInfo.targetHost,
                                    targetPort = connectionInfo.targetPort) {
                                if (!it.isSuccess) {
                                    logger.error {
                                        "Fail to write http data to proxy, close the agent channel, body type = ${
                                        bodyType
                                    }, user token = ${
                                        connectionInfo.userToken
                                    }, target host = ${
                                        connectionInfo.targetHost
                                    }, target port = ${
                                        connectionInfo.targetPort
                                    }, proxy channel = ${
                                        proxyChannel.id().asLongText()
                                    }, agent channel = ${
                                        agentChannel.id().asLongText()
                                    }"
                                }
                                agentChannel.close()
                            }
                        }
                        });
                return;
            }
        }
    }
}

