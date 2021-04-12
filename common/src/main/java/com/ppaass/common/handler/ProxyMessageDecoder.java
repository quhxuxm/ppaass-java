package com.ppaass.common.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.MessageSerializer;
import com.ppaass.common.message.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class ProxyMessageDecoder extends ByteToMessageDecoder {
    static {
        PpaassLogger.INSTANCE.register(ProxyMessageDecoder.class);
    }

    private final byte[] agentPrivateKey;

    public ProxyMessageDecoder(byte[] agentPrivateKey) {
        this.agentPrivateKey = agentPrivateKey;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ProxyMessage message = MessageSerializer.INSTANCE.decodeProxyMessage(in, this.agentPrivateKey);
        PpaassLogger.INSTANCE
                .trace(ProxyMessageDecoder.class, () -> "Decode proxy message, channel = {}, proxy message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                message
                        });
        out.add(message);
    }
}
