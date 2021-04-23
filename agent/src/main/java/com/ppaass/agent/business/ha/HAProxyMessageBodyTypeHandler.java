package com.ppaass.agent.business.ha;

import com.ppaass.agent.AgentConfiguration;
import com.ppaass.agent.IAgentConst;
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
class HAProxyMessageBodyTypeHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    private final AgentConfiguration agentConfiguration;

    HAProxyMessageBodyTypeHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext proxyChannelContext, Throwable cause) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        PpaassLogger.INSTANCE.error(() -> "Proxy channel exception happen, proxy channel = {}",
                () -> new Object[]{proxyChannel.id().asLongText(), cause});
        proxyChannel.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext proxyChannelContext) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannelsOnProxyChannel = proxyChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
        agentChannelsOnProxyChannel.forEach((agentChannelId, agentChannelWrapper) -> {
            agentChannelWrapper.close();
        });
        var channelPool =
                proxyChannel.attr(IHAConstant.IProxyChannelConstant.CHANNEL_POOL)
                        .get();
        proxyChannel.attr(IHAConstant.IProxyChannelConstant.CLOSED_ALREADY).set(true);
        channelPool.invalidateObject(proxyChannel, DestroyMode.ABANDONED);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage) throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var agentChannelsOnProxyChannel = proxyChannel.attr(IAgentConst.IProxyChannelAttr.AGENT_CHANNELS).get();
        var agentChannelWrapper = agentChannelsOnProxyChannel.get(proxyMessage.getBody().getAgentChannelId());
        if (agentChannelWrapper == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "The agent channel id in proxy message is not for current proxy channel, discard the proxy message, proxy channel = {}, proxy message:\n{}\n",
                    () -> new Object[]{
                            proxyChannel.id().asLongText(),
                            proxyMessage
                    });
            return;
        }
        var agentChannel = agentChannelWrapper.getChannel();
        var connectionInfo = agentChannel.attr(IHAConstant.IAgentChannelConstant.HTTP_CONNECTION_INFO).get();
        switch (proxyMessage.getBody().getBodyType()) {
            case TCP_CONNECT_FAIL -> {
                PpaassLogger.INSTANCE.error(
                        () -> "Connect fail for uri: [{}], close it, agent channel = {}, proxy channel = {}",
                        () -> new Object[]{
                                connectionInfo.getUri(), agentChannel.id().asLongText(), proxyChannel.id().asLongText()
                        });
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
                                            IHAConstant.CONNECTION_ESTABLISHED));
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
                                var failResponse =
                                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                            });
                    return;
                }
                //HTTP
                HAUtil.INSTANCE.writeAgentMessageToProxy(
                        AgentMessageBodyType.TCP_DATA,
                        agentConfiguration.getUserToken(),
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
                            proxyChannel.close();
                            var failResponse =
                                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                            agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
                        }
                );
            }
            case TCP_DATA_SUCCESS -> {
                proxyChannel.attr(IHAConstant.IProxyChannelConstant.AGENT_CHANNEL_TO_SEND_PURE_DATA).set(agentChannel);
                proxyChannelContext.fireChannelRead(proxyMessage);
            }
            case TCP_CONNECTION_CLOSE -> {
                agentChannel.close();
            }
            case TCP_DATA_FAIL -> {
                PpaassLogger.INSTANCE.trace(
                        () -> "FAIL_TCP happen close connection, agent channel = {}, proxy channel = {}.",
                        () -> new Object[]{
                                connectionInfo.getUri(), agentChannel.id().asLongText(),
                                proxyChannel.id().asLongText()
                        });
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
                proxyChannel.close();
                var failResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                agentChannel.writeAndFlush(failResponse).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }
}
