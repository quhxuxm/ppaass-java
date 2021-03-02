package com.ppaass.agent.handler.http;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.handler.http.bo.HttpConnectionInfo;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.AgentMessageBodyType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

class HttpAgentProxyConnectListener implements ChannelFutureListener {
    private final Channel agentChannel;
    private final HttpConnectionInfo connectionInfo;
    private final AgentConfiguration agentConfiguration;
    private final Object messageCarriedOnConnectTime;

    static {
        PpaassLogger.INSTANCE.register(HttpAgentProxyConnectListener.class);
    }

    public HttpAgentProxyConnectListener(Channel agentChannel,
                                         HttpConnectionInfo connectionInfo,
                                         AgentConfiguration agentConfiguration,
                                         Object messageCarriedOnConnectTime) {
        this.agentChannel = agentChannel;
        this.connectionInfo = connectionInfo;
        this.agentConfiguration = agentConfiguration;
        this.messageCarriedOnConnectTime = messageCarriedOnConnectTime;
    }

    @Override
    public void operationComplete(ChannelFuture proxyChannelConnectFuture) throws Exception {
        if (!proxyChannelConnectFuture.isSuccess()) {
            PpaassLogger.INSTANCE.error(HttpAgentProxyConnectListener.class,
                    () -> "Fail to create HTTP/HTTPS connection to proxy because of exception, agent channel = {}, proxy channel = {}.",
                    () -> new Object[]{
                            agentChannel.id().asLongText(), proxyChannelConnectFuture.channel().id().asLongText(),
                            proxyChannelConnectFuture.cause()
                    });
            agentChannel.close();
            return;
        }
        var proxyChannel = proxyChannelConnectFuture.channel();
        connectionInfo.setAgentChannel(agentChannel);
        connectionInfo.setProxyChannel(proxyChannel);
        connectionInfo.setUserToken(agentConfiguration.getUserToken());
        connectionInfo.setHttpMessageCarriedOnConnectTime(this.messageCarriedOnConnectTime);
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
                                return;
                            }
                            PpaassLogger.INSTANCE.error(HttpAgentProxyConnectListener.class,
                                    () -> "Fail to write HTTP/HTTPS connection data to proxy because of exception, agent channel = {}, proxy channel = {}.",
                                    () -> new Object[]{
                                            agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                                            proxyChannelWriteFuture.cause()
                                    });
                            proxyChannel.close().addListener(future -> {
                                var failResponse =
                                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                            });
                        });
    }
}
