package com.ppaass.proxy.handler;

import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.MessageSerializer;
import com.ppaass.common.message.ProxyMessage;
import com.ppaass.common.message.ProxyMessageBody;
import com.ppaass.common.message.ProxyMessageBodyType;
import com.ppaass.proxy.IProxyConstant;
import com.ppaass.proxy.ProxyConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Service;

@Service
@ChannelHandler.Sharable
public class T2PTcpChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    static {
        PpaassLogger.INSTANCE.register(T2PTcpChannelHandler.class);
    }

    public T2PTcpChannelHandler(ProxyConfiguration proxyConfiguration) {
    }

    @Override
    public void channelActive(ChannelHandlerContext targetChannelContext) throws Exception {
        super.channelActive(targetChannelContext);
        var targetChannel = targetChannelContext.channel();
        targetChannel.read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext targetChannelContext) throws Exception {
        super.channelReadComplete(targetChannelContext);
        var targetChannel = targetChannelContext.channel();
        var connectionInfo = targetChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
        if (connectionInfo != null) {
            var proxyChannel = connectionInfo.getProxyTcpChannel();
            if (proxyChannel.isWritable()) {
                targetChannel.read();
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext targetChannelContext, ByteBuf targetOriginalMessageBuf)
            throws Exception {
        var targetChannel = targetChannelContext.channel();
        var connectionInfo = targetChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            PpaassLogger.INSTANCE.error(T2PTcpChannelHandler.class,
                    () -> "Fail to transfer data from target to proxy because of no agent connection information attached, target channel = {}.",
                    () -> new Object[]{
                            targetChannel.id().asLongText()
                    });
            targetChannel.close();
            return;
        }
        var proxyChannel = connectionInfo.getProxyTcpChannel();
        final byte[] originalDataByteArray = new byte[targetOriginalMessageBuf.readableBytes()];
        targetOriginalMessageBuf.readBytes(originalDataByteArray);
        var proxyMessageBody =
                new ProxyMessageBody(
                        MessageSerializer.INSTANCE.generateUuid(),
                        connectionInfo.getUserToken(),
                        connectionInfo.getTargetHost(),
                        connectionInfo.getTargetPort(),
                        ProxyMessageBodyType.OK_TCP,
                        originalDataByteArray);
        var proxyMessage = new ProxyMessage(
                MessageSerializer.INSTANCE.generateUuidInBytes(),
                EncryptionType.choose(),
                proxyMessageBody);
        proxyChannel.writeAndFlush(proxyMessage)
                .addListener((ChannelFutureListener) proxyChannelFuture -> {
                    if (proxyChannelFuture.isSuccess()) {
                        targetChannel.read();
                        return;
                    }
                    PpaassLogger.INSTANCE.error(T2PTcpChannelHandler.class,
                            () -> "Fail to write proxy message to agent because of exception, proxy channel = {}, target channel = {}",
                            () -> new Object[]{
                                    proxyChannel.id().asLongText(), targetChannel.id().asLongText(),
                                    proxyChannelFuture.cause()
                            });
                    targetChannel.close();
                    proxyChannel.close();
                });
    }
}
