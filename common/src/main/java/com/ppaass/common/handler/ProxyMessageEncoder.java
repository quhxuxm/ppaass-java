package com.ppaass.common.handler;

import com.ppaass.common.message.ProxyMessage;
import com.ppaass.common.message.MessageSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> {
    private final byte[] agentPublicKey;

    public ProxyMessageEncoder(byte[] agentPublicKey) {
        this.agentPublicKey = agentPublicKey;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) throws Exception {
        MessageSerializer.INSTANCE.encodeMessage(msg, this.agentPublicKey, out);
    }
}
