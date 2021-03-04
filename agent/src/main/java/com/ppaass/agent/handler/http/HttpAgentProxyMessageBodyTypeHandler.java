package com.ppaass.agent.handler.http;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.AgentMessageBodyType;
import com.ppaass.common.message.HeartbeatInfo;
import com.ppaass.common.message.MessageSerializer;
import com.ppaass.common.message.ProxyMessage;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class HttpAgentProxyMessageBodyTypeHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    static {
        PpaassLogger.INSTANCE.register(HttpAgentProxyMessageBodyTypeHandler.class);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var connectionInfo = proxyChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            PpaassLogger.INSTANCE.error(HttpAgentProxyMessageBodyTypeHandler.class,
                    () -> "Close proxy channel because of connection info not exist, proxy channel = {}",
                    () -> new Object[]{
                            proxyChannel.id().asLongText()
                    });
            proxyChannel.close();
            return;
        }
        var agentChannel = connectionInfo.getAgentChannel();
        switch (proxyMessage.getBody().getBodyType()) {
            case HEARTBEAT -> {
                var originalData = proxyMessage.getBody().getData();
                var heartbeat = MessageSerializer.JSON_OBJECT_MAPPER.readValue(originalData, HeartbeatInfo.class);
                PpaassLogger.INSTANCE.trace(HttpAgentProxyMessageBodyTypeHandler.class,
                        () -> "[HEARTBEAT FROM PROXY]: agent channel = {}, proxy channel = {}, heartbeat id = {}, heartbeat time = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText(), proxyChannel.id().asLongText(), heartbeat.getId(),
                                heartbeat.getUtcDateTime()
                        });
            }
            case CONNECT_FAIL -> {
                PpaassLogger.INSTANCE.error(HttpAgentProxyMessageBodyTypeHandler.class,
                        () -> "Connect fail for uri: [{}], close it, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                connectionInfo.getUri(), agentChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
                proxyChannel.close().addListener(future -> {
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                });
            }
            case CONNECT_SUCCESS -> {
                if (connectionInfo.isHttps()) {
                    //HTTPS
                    var okResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                    IHttpAgentConstant.CONNECTION_ESTABLISHED);
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
                                    agentChannel.read();
                                    proxyChannel.read();
                                    return;
                                }
                                PpaassLogger.INSTANCE.trace(HttpAgentProxyMessageBodyTypeHandler.class,
                                        () -> "Fail to write CONNECT_SUCCESS to client because of exception, uri=[{}] agent channel = {}, proxy channel = {}.",
                                        () -> new Object[]{
                                                connectionInfo.getUri(), agentChannel.id().asLongText(),
                                                proxyChannel.id().asLongText(),
                                                agentWriteChannelFuture.cause()
                                        });
                                proxyChannel.close().addListener(future -> {
                                    var failResponse =
                                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                                });
                            });
                    return;
                }
                //HTTP
                HttpAgentUtil.INSTANCE.writeAgentMessageToProxy(
                        AgentMessageBodyType.TCP_DATA,
                        connectionInfo.getUserToken(),
                        proxyChannel,
                        connectionInfo.getHttpMessageCarriedOnConnectTime(),
                        connectionInfo.getTargetHost(),
                        connectionInfo.getTargetPort(),
                        proxyWriteChannelFuture -> {
                            if (proxyWriteChannelFuture.isSuccess()) {
                                agentChannel.read();
                                proxyChannel.read();
                                return;
                            }
                            PpaassLogger.INSTANCE.trace(HttpAgentProxyMessageBodyTypeHandler.class,
                                    () -> "Fail to write HTTP DATA to from agent to proxy because of exception, uri=[{}] agent channel = {}, proxy channel = {}.",
                                    () -> new Object[]{
                                            connectionInfo.getUri(), agentChannel.id().asLongText(),
                                            proxyChannel.id().asLongText(),
                                            proxyWriteChannelFuture.cause()
                                    });
                            proxyChannel.close().addListener(future -> {
                                var failResponse =
                                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                            });
                        }
                );
            }
            case OK_TCP -> {
                proxyChannelContext.fireChannelRead(proxyMessage);
            }
            case FAIL_TCP -> {
                PpaassLogger.INSTANCE.trace(HttpAgentProxyMessageBodyTypeHandler.class,
                        () -> "FAIL_TCP happen close connection, agent channel = {}, proxy channel = {}.",
                        () -> new Object[]{
                                connectionInfo.getUri(), agentChannel.id().asLongText(),
                                proxyChannel.id().asLongText()
                        });
                proxyChannel.close().addListener(future -> {
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                });
            }
            case OK_UDP, FAIL_UDP -> {
                PpaassLogger.INSTANCE.trace(HttpAgentProxyMessageBodyTypeHandler.class,
                        () -> "No OK_UDP proxy message body type for HTTP agent, close it, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                agentChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
                proxyChannel.close().addListener(future -> {
                    var failResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                });
            }
        }
    }
}
