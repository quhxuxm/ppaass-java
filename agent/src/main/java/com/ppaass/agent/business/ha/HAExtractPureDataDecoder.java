package com.ppaass.agent.business.ha;

import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class HAExtractPureDataDecoder extends MessageToMessageDecoder<ProxyMessage> {
    private final Logger logger = LoggerFactory.getLogger(HAExtractPureDataDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage, List<Object> out)
            throws Exception {
        var proxyChannel = proxyChannelContext.channel();
        var originalDataByteBuffer = Unpooled.wrappedBuffer(proxyMessage.getBody().getData());
        logger.trace(
                "Receive original proxy data, agent channel = {}, proxy channel = {}, original proxy data: \n{}\n",
                proxyMessage.getBody().getAgentChannelId(), proxyChannel.id().asLongText(),
                ByteBufUtil.prettyHexDump(originalDataByteBuffer)
        );
        out.add(originalDataByteBuffer);
    }
}
