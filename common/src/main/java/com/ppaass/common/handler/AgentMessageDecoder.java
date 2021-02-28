package com.ppaass.common.handler;

import com.ppaass.common.message.MessageSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

public class AgentMessageDecoder extends ByteToMessageDecoder {
    private final byte[] proxyPrivateKey;

    public AgentMessageDecoder(byte[] proxyPrivateKey) {
        this.proxyPrivateKey = proxyPrivateKey;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        var message = MessageSerializer.INSTANCE.decodeAgentMessage(in, this.proxyPrivateKey);
        out.add(message);
        ReferenceCountUtil.release(in);
    }
}
