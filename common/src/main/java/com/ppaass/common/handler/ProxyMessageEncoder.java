package com.ppaass.common.handler;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();

    @Override
    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) throws Exception {
        MessageCodec.INSTANCE.encodeProxyMessage(msg, out);
        logger
                .trace(ProxyMessageEncoder.class, () -> "Encode proxy message, channel = {}, proxy message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                msg
                        });
    }
}
