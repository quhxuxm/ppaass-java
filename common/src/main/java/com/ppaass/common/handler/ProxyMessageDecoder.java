package com.ppaass.common.handler;

import com.ppaass.common.message.MessageSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

public class ProxyMessageDecoder extends ByteToMessageDecoder {
    private final byte[] agentPrivateKey;

    public ProxyMessageDecoder(byte[] agentPrivateKey) {
        this.agentPrivateKey = agentPrivateKey;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        var message = MessageSerializer.INSTANCE.decodeProxyMessage(in, this.agentPrivateKey);
        out.add(message);
        ReferenceCountUtil.release(in);
    }
}
