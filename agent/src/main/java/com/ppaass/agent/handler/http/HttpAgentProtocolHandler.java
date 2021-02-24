package com.ppaass.agent.handler.http;

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
                connectionInfo.setKeepAlive( connectionKeepAlive);
                HttpAgentUtil.INSTANCE.  proxyBootstrapForHttps.connect(agentConfiguration.proxyHost,
                        agentConfiguration.proxyPort !!)
                        .addListener(
                        HttpProxyConnectListener(agentChannel, connectionInfo,
                                agentConfiguration,
                                null,
                                proxyBootstrapForHttps))
                return
            }
        }
    }
}
}
