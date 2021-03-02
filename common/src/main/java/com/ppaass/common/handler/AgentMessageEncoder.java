package com.ppaass.common.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.AgentMessage;
import com.ppaass.common.message.MessageSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class AgentMessageEncoder extends MessageToByteEncoder<AgentMessage> {
    static {
        PpaassLogger.INSTANCE.register(AgentMessageEncoder.class);
    }

    private final byte[] proxyPublicKey;

    public AgentMessageEncoder(byte[] proxyPublicKey) {
        this.proxyPublicKey = proxyPublicKey;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, AgentMessage msg, ByteBuf out) throws Exception {
        MessageSerializer.INSTANCE.encodeMessage(msg, this.proxyPublicKey, out);
        PpaassLogger.INSTANCE
                .trace(AgentMessageEncoder.class, () -> "Encode agent message, channel = {}, agent message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                msg
                        });
    }
}
