package com.ppaass.common.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.common.message.MessageSerializer;
import com.ppaass.common.message.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> {
    static {
        PpaassLogger.INSTANCE.register(ProxyMessageEncoder.class);
    }

    private final byte[] agentPublicKey;

    public ProxyMessageEncoder(byte[] agentPublicKey) {
        this.agentPublicKey = agentPublicKey;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) throws Exception {
        MessageSerializer.INSTANCE.encodeMessage(msg, this.agentPublicKey, out);
        PpaassLogger.INSTANCE
                .trace(ProxyMessageDecoder.class, () -> "Encode proxy message, channel = {}, proxy message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                msg
                        });
    }
}
