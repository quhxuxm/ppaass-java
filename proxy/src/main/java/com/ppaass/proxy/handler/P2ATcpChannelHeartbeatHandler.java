package com.ppaass.proxy.handler;

import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.message.*;
import com.ppaass.proxy.IProxyConstant;
import com.ppaass.proxy.ProxyConfiguration;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.TimeZone;

@Service
@ChannelHandler.Sharable
public class P2ATcpChannelHeartbeatHandler extends ChannelInboundHandlerAdapter {
    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");
    private static final Logger logger = LoggerFactory.getLogger(P2ATcpChannelHeartbeatHandler.class);
    private final ProxyConfiguration proxyConfiguration;

    public P2ATcpChannelHeartbeatHandler(ProxyConfiguration proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext proxyChannelContext, Object evt) throws Exception {
        if (!(evt instanceof IdleStateEvent)) {
            proxyChannelContext.fireUserEventTriggered(evt);
            return;
        }
        IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
        if (IdleState.ALL_IDLE != idleStateEvent.state()) {
            proxyChannelContext.fireUserEventTriggered(idleStateEvent);
            return;
        }
        var heartbeat = new HeartbeatInfo(MessageSerializer.INSTANCE.generateUuid(),
                Calendar.getInstance(UTC_TIME_ZONE).getTime().getTime());
        var proxyChannel = proxyChannelContext.channel();
        var udpConnectionInfo =
                proxyChannel.attr(IProxyConstant.UDP_CONNECTION_INFO).get();
        if (udpConnectionInfo != null) {
            return;
        }
        var tcpConnectionInfo =
                proxyChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
        if (tcpConnectionInfo == null) {
            return;
        }
        var messageBody = new ProxyMessageBody(
                MessageSerializer.INSTANCE.generateUuid(),
                tcpConnectionInfo.getUserToken(),
                tcpConnectionInfo.getTargetHost(),
                tcpConnectionInfo.getTargetPort(),
                ProxyMessageBodyType.HEARTBEAT,
                MessageSerializer.JSON_OBJECT_MAPPER.writeValueAsBytes(heartbeat)
        );
        var heartbeatMessage =
                new ProxyMessage(
                        MessageSerializer.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        messageBody
                );
        proxyChannel.writeAndFlush(heartbeatMessage).addListener((ChannelFutureListener) proxyChannelFuture -> {
            if (proxyChannelFuture.isSuccess()) {
                tcpConnectionInfo.setHeartBeatFailureTimes(0);
                logger.debug("Heartbeat success with agent, proxy channel = {}, target channel = {}",
                        proxyChannel.id().asLongText(), tcpConnectionInfo.getTargetTcpChannel().id().asLongText());
                return;
            }
            if (tcpConnectionInfo.getHeartBeatFailureTimes() >= proxyConfiguration.getProxyTcpChannelHeartbeatRetry()) {
                logger.error("Heartbeat fail with agent, close it, time = {}, proxy channel = {}, target channel = {}",
                        tcpConnectionInfo.getHeartBeatFailureTimes(), proxyChannel.id().asLongText(),
                        tcpConnectionInfo.getTargetTcpChannel().id().asLongText());
                proxyChannel.close();
                tcpConnectionInfo.getTargetTcpChannel().close();
                return;
            }
            logger.error("Heartbeat fail with agent, time = {}, proxy channel = {}, target channel = {}",
                    tcpConnectionInfo.getHeartBeatFailureTimes(), proxyChannel.id().asLongText(),
                    tcpConnectionInfo.getTargetTcpChannel().id().asLongText());
            tcpConnectionInfo.setHeartBeatFailureTimes(tcpConnectionInfo.getHeartBeatFailureTimes() + 1);
        });
    }
}
