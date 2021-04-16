package com.ppaass.common.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.AgentMessage;
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
        MessageCodec.INSTANCE.encodeAgentMessage(msg, this.proxyPublicKey, out);
        PpaassLogger.INSTANCE
                .trace(AgentMessageEncoder.class, () -> "Encode agent message, channel = {}, agent message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                msg
                        });
    }
}
