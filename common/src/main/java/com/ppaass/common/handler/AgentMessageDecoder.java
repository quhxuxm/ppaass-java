package com.ppaass.common.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.AgentMessage;
import com.ppaass.common.message.MessageSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class AgentMessageDecoder extends ByteToMessageDecoder {
    static {
        PpaassLogger.INSTANCE.register(AgentMessageDecoder.class);
    }

    private final byte[] proxyPrivateKey;

    public AgentMessageDecoder(byte[] proxyPrivateKey) {
        this.proxyPrivateKey = proxyPrivateKey;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        AgentMessage message = MessageSerializer.INSTANCE.decodeAgentMessage(in, this.proxyPrivateKey);
        PpaassLogger.INSTANCE
                .trace(AgentMessageDecoder.class, () -> "Decode agent message, channel = {}, agent message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                message
                        });
        out.add(message);
    }
}
