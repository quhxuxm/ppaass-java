package com.ppaass.common.handler;

import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> {
    private final Logger logger = LoggerFactory.getLogger(ProxyMessageEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) throws Exception {
        MessageCodec.INSTANCE.encodeProxyMessage(msg, out);
        logger
                .trace("Encode proxy message, channel = {}, proxy message = {}",
                        ctx.channel().id().asLongText(),
                        msg
                );
    }
}
