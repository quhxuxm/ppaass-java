package com.ppaass.proxy.handler;

import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.message.MessageSerializer;
import com.ppaass.common.message.ProxyMessage;
import com.ppaass.common.message.ProxyMessageBody;
import com.ppaass.common.message.ProxyMessageBodyType;
import com.ppaass.proxy.IProxyConstant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@ChannelHandler.Sharable
public class T2PTcpChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(T2PTcpChannelHandler.class);

    @Override
    public void channelActive(ChannelHandlerContext targetChannelContext) throws Exception {
        super.channelActive(targetChannelContext);
        var targetChannel = targetChannelContext.channel();
        targetChannel.read();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext targetChannelContext, ByteBuf targetOriginalMessageBuf)
            throws Exception {
        var targetChannel = targetChannelContext.channel();
        var agentTcpConnectionInfo = targetChannel.attr(IProxyConstant.TCP_CONNECTION_INFO).get();
        if (agentTcpConnectionInfo == null) {
            logger.error(
                    "Fail to transfer data from target to proxy because of no agent connection information attached, target channel = {}.",
                    targetChannel.id().asLongText());
            targetChannel.close();
            return;
        }
        var proxyChannel = agentTcpConnectionInfo.getProxyTcpChannel();
        var originalDataByteArray = new byte[targetOriginalMessageBuf.readableBytes()];
        targetOriginalMessageBuf.readBytes(originalDataByteArray);
        var proxyMessageBody =
                new ProxyMessageBody(
                        MessageSerializer.INSTANCE.generateUuid(),
                        agentTcpConnectionInfo.getUserToken(),
                        agentTcpConnectionInfo.getTargetHost(),
                        agentTcpConnectionInfo.getTargetPort(),
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
                        proxyChannel.read();
                        return;
                    }
                    logger.error(
                            "Fail to write proxy message to agent because of exception, proxy channel = {}, target channel = {}",
                            proxyChannel.id().asLongText(), targetChannel.id().asLongText(),
                            proxyChannelFuture.cause());
                    targetChannel.close();
                    proxyChannel.close();
                });
    }
}
