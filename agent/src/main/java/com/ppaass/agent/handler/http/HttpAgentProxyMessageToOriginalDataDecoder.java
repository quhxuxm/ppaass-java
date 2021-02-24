package com.ppaass.agent.handler.http;

import com.ppaass.common.message.ProxyMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

class HttpAgentProxyMessageToOriginalDataDecoder extends MessageToMessageDecoder<ProxyMessage> {
    @Override
    protected void decode(ChannelHandlerContext proxyChannelContext, ProxyMessage proxyMessage, List<Object> out)
            throws Exception {
        out.add(Unpooled.wrappedBuffer(proxyMessage.getBody().getData()));
    }
}
