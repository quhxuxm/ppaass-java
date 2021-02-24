package com.ppaass.agent.handler.http;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.handler.http.bo.HttpConnectionInfo;
import com.ppaass.common.message.AgentMessageBodyType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HttpAgentProxyConnectListener implements ChannelFutureListener {
    private static final Logger logger = LoggerFactory.getLogger(HttpAgentProxyConnectListener.class);
    private final Channel agentChannel;
    private final HttpConnectionInfo connectionInfo;
    private final AgentConfiguration agentConfiguration;

    public HttpAgentProxyConnectListener(Channel agentChannel,
                                         HttpConnectionInfo connectionInfo,
                                         AgentConfiguration agentConfiguration) {
        this.agentChannel = agentChannel;
        this.connectionInfo = connectionInfo;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void operationComplete(ChannelFuture proxyChannelConnectFuture) throws Exception {
        if (!proxyChannelConnectFuture.isSuccess()) {
            logger.error(
                    "Fail to create HTTP/HTTPS connection to proxy because of exception, agent channel = {}, proxy channel = {}.",
                    agentChannel.id().asLongText(), proxyChannelConnectFuture.channel().id().asLongText(),
                    proxyChannelConnectFuture.cause());
            agentChannel.close();
            return;
        }
        var proxyChannel = proxyChannelConnectFuture.channel();
        connectionInfo.setAgentChannel(agentChannel);
        connectionInfo.setProxyChannel(proxyChannel);
        connectionInfo.setUserToken(agentConfiguration.getUserToken());
        connectionInfo.setHttpMessageCarriedOnConnectTime(null);
        proxyChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).setIfAbsent(connectionInfo);
        agentChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).setIfAbsent(connectionInfo);
        AgentMessageBodyType bodyType = null;
        if (connectionInfo.isKeepAlive()) {
            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
            agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
            bodyType = AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE;
        } else {
            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false);
            agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false);
            bodyType = AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE;
        }
        HttpAgentUtil.INSTANCE
                .writeAgentMessageToProxy(bodyType, connectionInfo.getUserToken(), proxyChannel,
                        null, connectionInfo.getTargetHost(), connectionInfo.getTargetPort(),
                        proxyChannelWriteFuture -> {
                            if (proxyChannelWriteFuture.isSuccess()) {
                                proxyChannel.read();
                                return;
                            }
                            logger.error(
                                    "Fail to write HTTP/HTTPS connection data to proxy because of exception, agent channel = {}, proxy channel = {}.",
                                    agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                                    proxyChannelWriteFuture.cause());
                            agentChannel.close();
                            proxyChannel.close();
                        });
    }
}
