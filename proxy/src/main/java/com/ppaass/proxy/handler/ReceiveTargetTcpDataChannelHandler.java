package com.ppaass.proxy.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.message.EncryptionType;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import com.ppaass.protocol.vpn.message.ProxyMessageBody;
import com.ppaass.protocol.vpn.message.ProxyMessageBodyType;
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
public class ReceiveTargetTcpDataChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final ProxyConfiguration proxyConfiguration;

    public ReceiveTargetTcpDataChannelHandler(ProxyConfiguration proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext targetChannelContext, Throwable cause) throws Exception {
        PpaassLogger.INSTANCE.error(() -> "Exception happen on target channel.", () -> new Object[]{cause});
    }

    @Override
    public void channelInactive(ChannelHandlerContext targetChannelContext) {
        var targetChannel = targetChannelContext.channel();
        var targetTcpInfo = targetChannel.attr(IProxyConstant.ITargetChannelAttr.TCP_INFO).get();
        if (targetTcpInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "Fail to transfer data from target to proxy because of no tcp info attached, target channel = {}.",
                    () -> new Object[]{
                            targetChannel.id().asLongText()
                    });
            targetChannel.close();
            return;
        }
        var proxyChannel = targetTcpInfo.getProxyTcpChannel();
        var targetChannels = proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNELS).get();
        var targetChannelKey = String.format(IProxyConstant.TARGET_CHANNEL_KEY_FORMAT,
                targetTcpInfo.getAgentInstanceId(), targetTcpInfo.getAgentChannelId());
        targetChannels.remove(targetChannelKey);
        var proxyMessageBody =
                new ProxyMessageBody(
                        UUIDUtil.INSTANCE.generateUuid(),
                        proxyConfiguration.getProxyInstanceId(),
                        targetTcpInfo.getUserToken(),
                        targetTcpInfo.getSourceHost(),
                        targetTcpInfo.getSourcePort(),
                        targetTcpInfo.getTargetHost(),
                        targetTcpInfo.getTargetPort(),
                        ProxyMessageBodyType.TCP_CONNECTION_CLOSE,
                        targetTcpInfo.getAgentChannelId(),
                        targetTcpInfo.getTargetChannelId(),
                        null);
        var proxyMessage = new ProxyMessage(
                UUIDUtil.INSTANCE.generateUuidInBytes(),
                EncryptionType.choose(),
                proxyMessageBody);
        proxyChannel.writeAndFlush(proxyMessage).addListener(future -> {
            if (future.isSuccess()) {
                PpaassLogger.INSTANCE.debug(() -> "Success to write TCP_CONNECTION_CLOSE to agent, tcp info:\n{}\n",
                        () -> new Object[]{targetTcpInfo});
                return;
            }
            PpaassLogger.INSTANCE.error(() -> "Fail to write TCP_CONNECTION_CLOSE to agent, tcp info:\n{}\n",
                    () -> new Object[]{targetTcpInfo});
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext targetChannelContext, ByteBuf targetOriginalMessageBuf) {
        var targetChannel = targetChannelContext.channel();
        var targetTcpInfo = targetChannel.attr(IProxyConstant.ITargetChannelAttr.TCP_INFO).get();
        if (targetTcpInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "Fail to transfer data from target to agent because of no tcp info attached, target channel = {}.",
                    () -> new Object[]{
                            targetChannel.id().asLongText()
                    });
            targetChannel.close();
            return;
        }
        var proxyChannel = targetTcpInfo.getProxyTcpChannel();
        final byte[] originalDataByteArray = new byte[targetOriginalMessageBuf.readableBytes()];
        targetOriginalMessageBuf.readBytes(originalDataByteArray);
        var proxyMessageBody =
                new ProxyMessageBody(
                        UUIDUtil.INSTANCE.generateUuid(),
                        proxyConfiguration.getProxyInstanceId(),
                        targetTcpInfo.getUserToken(),
                        targetTcpInfo.getSourceHost(),
                        targetTcpInfo.getSourcePort(),
                        targetTcpInfo.getTargetHost(),
                        targetTcpInfo.getTargetPort(),
                        ProxyMessageBodyType.TCP_DATA_SUCCESS,
                        targetTcpInfo.getAgentChannelId(),
                        targetTcpInfo.getTargetChannelId(),
                        originalDataByteArray);
        var proxyMessage = new ProxyMessage(
                UUIDUtil.INSTANCE.generateUuidInBytes(),
                EncryptionType.choose(),
                proxyMessageBody);
        proxyChannel.writeAndFlush(proxyMessage)
                .addListener((ChannelFutureListener) proxyChannelFuture -> {
                    if (proxyChannelFuture.isSuccess()) {
                        PpaassLogger.INSTANCE.debug(
                                () -> "Success to write target data to agent, tcp info: \n{}\n",
                                () -> new Object[]{
                                        targetTcpInfo
                                });
                        return;
                    }
                    PpaassLogger.INSTANCE.error(
                            () -> "Fail to write target data to agent because of exception, tcp info: \n{}\n",
                            () -> new Object[]{
                                    targetTcpInfo
                            });
                    targetChannel.close();
                });
    }
}
