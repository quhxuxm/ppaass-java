package com.ppaass.proxy.handler;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.common.util.UUIDUtil;
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
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final ProxyConfiguration proxyConfiguration;
    private static final int TARGET_DATA_MAX_FRAME_LENGTH = 1024 * 1024 * 1000;

    public ReceiveTargetTcpDataChannelHandler(ProxyConfiguration proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext targetChannelContext, Throwable cause) throws Exception {
        logger.error(() -> "Exception happen on target channel.", () -> new Object[]{cause});
        var targetChannel = targetChannelContext.channel();
        var targetTcpInfo = targetChannel.attr(IProxyConstant.ITargetChannelAttr.TCP_INFO).get();
        if (targetTcpInfo == null) {
            if (targetChannel.isActive()) {
                targetChannel.close();
            }
            return;
        }
        var proxyChannel = targetTcpInfo.getProxyTcpChannel();
        proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNEL).set(null);
        proxyChannel.attr(IProxyConstant.IProxyChannelAttr.CLOSED_ALREADY).set(true);
        if (targetChannel.isActive()) {
            targetChannel.close();
        }
        if (proxyChannel.isActive()) {
            proxyChannel.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext targetChannelContext) {
        var targetChannel = targetChannelContext.channel();
        var targetTcpInfo = targetChannel.attr(IProxyConstant.ITargetChannelAttr.TCP_INFO).get();
        if (targetTcpInfo == null) {
            logger.error(
                    () -> "Fail to transfer data from target to proxy because of no tcp info attached, target channel = {}.",
                    () -> new Object[]{
                            targetChannel.id().asLongText()
                    });
            return;
        }
        var proxyChannel = targetTcpInfo.getProxyTcpChannel();
        var closedAlready = proxyChannel.attr(IProxyConstant.IProxyChannelAttr.CLOSED_ALREADY).get();
        if (closedAlready != null && closedAlready) {
            return;
        }
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
            proxyChannel.attr(IProxyConstant.IProxyChannelAttr.TARGET_CHANNEL).set(null);
            if (future.isSuccess()) {
                logger.debug(() -> "Success to write TCP_CONNECTION_CLOSE to agent, tcp info:\n{}\n",
                        () -> new Object[]{targetTcpInfo});
                return;
            }
            logger
                    .error(() -> "Fail to write TCP_CONNECTION_CLOSE to agent because of exception, tcp info:\n{}\n",
                            () -> new Object[]{targetTcpInfo, future.cause()});
            if (targetChannel.isActive()) {
                targetChannel.close();
            }
            if (proxyChannel.isActive()) {
                proxyChannel.close();
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext targetChannelContext, ByteBuf targetOriginalMessageBuf) {
        var targetChannel = targetChannelContext.channel();
        var targetTcpInfo = targetChannel.attr(IProxyConstant.ITargetChannelAttr.TCP_INFO).get();
        if (targetTcpInfo == null) {
            logger.error(
                    () -> "Fail to transfer data from target to agent because of no tcp info attached, target channel = {}.",
                    () -> new Object[]{
                            targetChannel.id().asLongText()
                    });
            if (targetChannel.isActive()) {
                targetChannel.close();
            }
            return;
        }
        var proxyChannel = targetTcpInfo.getProxyTcpChannel();
        while (targetOriginalMessageBuf.isReadable()) {
            int targetDataTotalLength = targetOriginalMessageBuf.readableBytes();
            int frameLength = TARGET_DATA_MAX_FRAME_LENGTH;
            if (targetDataTotalLength < frameLength) {
                frameLength = targetDataTotalLength;
            }
            long proxyChannelWritableBytes = proxyChannel.bytesBeforeWritable();
            if (frameLength > proxyChannelWritableBytes) {
                frameLength = (int) proxyChannelWritableBytes - 2048;
            }
            if (frameLength < 0) {
                frameLength = 0;
            }
            final byte[] originalDataByteArray = new byte[frameLength];
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
            if (!proxyChannel.isActive()) {
                if (targetChannel.isActive()) {
                    targetChannel.close();
                }
                logger.error(
                        () -> "Fail to write target data to agent because of proxy channel is not active, tcp info: \n{}\n",
                        () -> new Object[]{
                                targetTcpInfo
                        });
                break;
            }
            proxyChannel.writeAndFlush(proxyMessage).syncUninterruptibly()
                    .addListener((ChannelFutureListener) proxyChannelFuture -> {
                        if (proxyChannelFuture.isSuccess()) {
                            logger.debug(
                                    () -> "Success to write target data to agent, tcp info: \n{}\n",
                                    () -> new Object[]{
                                            targetTcpInfo
                                    });
                            return;
                        }
                        logger.error(
                                () -> "Fail to write target data to agent because of exception, tcp info: \n{}\n",
                                () -> new Object[]{
                                        targetTcpInfo,
                                        proxyChannelFuture.cause()
                                });
                        if (targetChannel.isActive()) {
                            targetChannel.close();
                        }
                        if (proxyChannel.isActive()) {
                            proxyChannel.close();
                        }
                    });
        }
    }
}
