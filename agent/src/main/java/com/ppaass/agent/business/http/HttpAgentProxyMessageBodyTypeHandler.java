package com.ppaass.agent.business.http;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.apache.commons.pool2.DestroyMode;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class HttpAgentProxyMessageBodyTypeHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    private final AgentConfiguration agentConfiguration;

    HttpAgentProxyMessageBodyTypeHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var connectionInfo =
                proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "Close proxy channel because of connection info not exist, proxy channel = {}",
                    () -> new Object[]{
                            proxyChannel.id().asLongText()
                    });
            var channelPool =
                    proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                            .get();
            channelPool.returnObject(proxyChannel);
            return;
        }
        var agentChannel = connectionInfo.getAgentChannel();
        switch (proxyMessage.getBody().getBodyType()) {
            case TCP_CONNECT_FAIL -> {
                PpaassLogger.INSTANCE.error(
                        () -> "Connect fail for uri: [{}], close it, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                connectionInfo.getUri(), agentChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
                var channelPool =
                        proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                                .get();
                channelPool.returnObject(proxyChannel);
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
                                            IHttpAgentConstant.CONNECTION_ESTABLISHED));
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
                                PpaassLogger.INSTANCE.trace(
                                        () -> "Fail to write CONNECT_SUCCESS to client because of exception, uri=[{}] agent channel = {}, proxy channel = {}.",
                                        () -> new Object[]{
                                                connectionInfo.getUri(), agentChannel.id().asLongText(),
                                                proxyChannel.id().asLongText(),
                                                agentWriteChannelFuture.cause()
                                        });
                                var channelPool =
                                        proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                                                .get();
                                channelPool.returnObject(proxyChannel);
                                var failResponse =
                                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                            });
                    return;
                }
                //HTTP
                HttpAgentUtil.INSTANCE.writeAgentMessageToProxy(
                        AgentMessageBodyType.TCP_DATA,
                        connectionInfo.getUserToken(),
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
                            PpaassLogger.INSTANCE.trace(
                                    () -> "Fail to write HTTP DATA to from agent to proxy because of exception, uri=[{}] agent channel = {}, proxy channel = {}.",
                                    () -> new Object[]{
                                            connectionInfo.getUri(), agentChannel.id().asLongText(),
                                            proxyChannel.id().asLongText(),
                                            proxyWriteChannelFuture.cause()
                                    });
                            var channelPool =
                                    proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                                            .get();
                            channelPool.invalidateObject(proxyChannel, DestroyMode.ABANDONED);
                            var failResponse =
                                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                        }
                );
            }
            case TCP_DATA_SUCCESS -> {
                proxyChannelContext.fireChannelRead(proxyMessage);
            }
            case TCP_CONNECTION_CLOSE -> {
                var channelPool =
                        proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                                .get();
                channelPool.returnObject(proxyChannel);
                agentChannel.close();
            }
            case TCP_DATA_FAIL -> {
                PpaassLogger.INSTANCE.trace(
                        () -> "FAIL_TCP happen close connection, agent channel = {}, proxy channel = {}.",
                        () -> new Object[]{
                                connectionInfo.getUri(), agentChannel.id().asLongText(),
                                proxyChannel.id().asLongText()
                        });
                var channelPool =
                        proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                                .get();
                channelPool.returnObject(proxyChannel);
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            }
            case UDP_DATA_FAIL, UDP_DATA_SUCCESS -> {
                PpaassLogger.INSTANCE.trace(
                        () -> "No OK_UDP proxy message body type for HTTP agent, close it, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
                var channelPool =
                        proxyChannel.attr(IHttpAgentConstant.IProxyChannelConstant.CHANNEL_POOL)
                                .get();
                channelPool.invalidateObject(proxyChannel, DestroyMode.ABANDONED);
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }
}
