package com.ppaass.common.handler;

import com.ppaass.common.message.AgentMessage;
import com.ppaass.common.message.MessageSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class AgentMessageEncoder extends MessageToByteEncoder<AgentMessage> {
    private final byte[] proxyPublicKey;

    public AgentMessageEncoder(byte[] proxyPublicKey) {
        this.proxyPublicKey = proxyPublicKey;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, AgentMessage msg, ByteBuf out) throws Exception {
        MessageSerializer.INSTANCE.encodeMessage(msg, this.proxyPublicKey, out);
    }
}
