package com.ppaass.common.handler;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.ProxyMessage;
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
        MessageCodec.INSTANCE.encodeProxyMessage(msg, this.agentPublicKey, out);
        PpaassLogger.INSTANCE
                .trace(ProxyMessageEncoder.class, () -> "Encode proxy message, channel = {}, proxy message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                msg
                        });
    }
}
