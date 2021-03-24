package com.ppaass.proxy.handler;

import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.*;
import com.ppaass.proxy.IProxyConstant;
import com.ppaass.proxy.ProxyConfiguration;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.TimeZone;

@Service
@ChannelHandler.Sharable
public class P2ATcpChannelHeartbeatHandler extends ChannelInboundHandlerAdapter {
    static {
        PpaassLogger.INSTANCE.register(P2ATcpChannelHeartbeatHandler.class);
    }

    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");

    public P2ATcpChannelHeartbeatHandler(ProxyConfiguration proxyConfiguration) {
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
        if (tcpConnectionInfo.isHeartbeatPending()) {
            PpaassLogger.INSTANCE.trace(P2ATcpChannelHeartbeatHandler.class,
                    () -> "Heartbeat to agent is in pending status, skip this heartbeat, proxy channel = {}, target channel = {}",
                    () -> new Object[]{
                            proxyChannel.id().asLongText(),
                            tcpConnectionInfo.getTargetTcpChannel().id().asLongText()
                    });
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
        tcpConnectionInfo.setHeartbeatPending(true);
        proxyChannel.writeAndFlush(heartbeatMessage).addListener((ChannelFutureListener) proxyChannelFuture -> {
            tcpConnectionInfo.setHeartbeatPending(false);
            if (proxyChannelFuture.isSuccess()) {
                PpaassLogger.INSTANCE.debug(P2ATcpChannelHeartbeatHandler.class,
                        () -> "Heartbeat success with agent, proxy channel = {}, target channel = {}",
                        () -> new Object[]{
                                proxyChannel.id().asLongText(),
                                tcpConnectionInfo.getTargetTcpChannel().id().asLongText()
                        });
                return;
            }
            PpaassLogger.INSTANCE.error(P2ATcpChannelHeartbeatHandler.class,
                    () -> "Heartbeat fail with agent, close it, proxy channel = {}, target channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText(),
                            tcpConnectionInfo.getTargetTcpChannel().id().asLongText()
                    });
            tcpConnectionInfo.getTargetTcpChannel().close().addListener(future -> {
                proxyChannel.close();
            });
        });
    }
}
