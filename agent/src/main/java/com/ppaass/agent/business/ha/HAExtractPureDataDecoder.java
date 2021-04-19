package com.ppaass.agent.business.ha;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

class HAExtractPureDataDecoder extends MessageToMessageDecoder<ProxyMessage> {
    @Override
    protected void decode(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage, List<Object> out)
            throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var connectionInfo = proxyChannel.attr(IHAConstant.IProxyChannelConstant.HTTP_CONNECTION_INFO).get();
        if (connectionInfo == null) {
            PpaassLogger.INSTANCE.error(
                    () -> "Close proxy channel because of connection info not exist, proxy channel = {}",
                    () -> new Object[]{proxyChannel.id().asLongText()});
            return;
        }
        var agentChannel = connectionInfo.getAgentChannel();
        var originalDataByteBuffer = Unpooled.wrappedBuffer(proxyMessage.getBody().getData());
        PpaassLogger.INSTANCE.trace(
                () -> "Receive original proxy data, agent channel = {}, proxy channel = {}, original proxy data: \n{}\n",
                () -> new Object[]{
                        agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                        ByteBufUtil.prettyHexDump(originalDataByteBuffer)
                });
        out.add(originalDataByteBuffer);
    }
}
