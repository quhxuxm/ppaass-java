package com.ppaass.common.handler;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class ProxyMessageDecoder extends ByteToMessageDecoder {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final byte[] agentPrivateKey;

    public ProxyMessageDecoder(byte[] agentPrivateKey) {
        this.agentPrivateKey = agentPrivateKey;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ProxyMessage message = MessageCodec.INSTANCE.decodeProxyMessage(in, this.agentPrivateKey);
        logger
                .trace(ProxyMessageDecoder.class, () -> "Decode proxy message, channel = {}, proxy message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                message
                        });
        out.add(message);
    }
}
