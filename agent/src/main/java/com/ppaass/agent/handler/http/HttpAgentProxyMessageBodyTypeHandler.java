package com.ppaass.agent.handler.http;

import com.ppaass.common.message.AgentMessageBodyType;
import com.ppaass.common.message.HeartbeatInfo;
import com.ppaass.common.message.MessageSerializer;
import com.ppaass.common.message.ProxyMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
class HttpAgentProxyMessageBodyTypeHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    private static final Logger logger = LoggerFactory.getLogger(HttpAgentProxyMessageBodyTypeHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var connectionInfo = proxyChannel.attr(IHttpAgentConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            proxyChannel.close();
            return;
        }
        var agentChannel = connectionInfo.getAgentChannel();
        switch (proxyMessage.getBody().getBodyType()) {
            case HEARTBEAT -> {
                var originalData = proxyMessage.getBody().getData();
                var heartbeat = MessageSerializer.JSON_OBJECT_MAPPER.readValue(originalData, HeartbeatInfo.class);
                logger.trace(
                        "[HEARTBEAT FROM PROXY]: agent channel = {}, proxy channel = {}, heartbeat id = {}, heartbeat time = {}",
                        agentChannel.id().asLongText(), proxyChannel.id().asLongText(), heartbeat.getId(),
                        heartbeat.getUtcDateTime());
                agentChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                        .addListener((ChannelFutureListener) agentChannelFuture -> {
                            if (agentChannelFuture.isSuccess()) {
                                logger.debug(
                                        "[HEARTBEAT TO CLIENT]: Success, agent channel = {},  proxy channel = {}",
                                        agentChannel.id().asLongText(),
                                        proxyChannel.id().asLongText());
                                proxyChannel.read();
                                return;
                            }
                            logger.error(
                                    "[HEARTBEAT TO CLIENT]: Fail, close it, agent channel = {},  proxy channel = {}",
                                    agentChannel.id().asLongText(),
                                    proxyChannel.id().asLongText());
                            agentChannel.close();
                            proxyChannel.close();
                        });
            }
            case CONNECT_FAIL -> {
                logger.error(
                        "Connect fail, close it, agent channel = {}, proxy channel = {}",
                        agentChannel.id().asLongText(), proxyChannel.id().asLongText());
                proxyChannel.close();
                agentChannel.close();
            }
            case CONNECT_SUCCESS -> {
                if (connectionInfo.isHttps()) {
                    //HTTPS
                    var okResponse =
                            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
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
                                logger.error(
                                        "Fail to write CONNECT_SUCCESS to client because of exception, agent channel = {}, proxy channel = {}.",
                                        agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                                        agentWriteChannelFuture.cause());
                                proxyChannel.close();
                                agentChannel.close();
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
                            proxyChannel.close();
                            agentChannel.close();
                        }
                );
            }
            case OK_TCP -> {
                proxyChannelContext.fireChannelRead(proxyMessage);
            }
            case OK_UDP -> {
                logger.error(
                        "No OK_UDP proxy message body type for HTTP agent, close it, agent channel = {}, proxy channel = {}",
                        agentChannel.id().asLongText(), proxyChannel.id().asLongText());
                proxyChannel.close();
                agentChannel.close();
            }
        }
    }
}
