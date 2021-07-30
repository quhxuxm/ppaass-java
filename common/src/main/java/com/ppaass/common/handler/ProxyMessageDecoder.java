package com.ppaass.common.handler;

import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProxyMessageDecoder extends ByteToMessageDecoder {
    private final Logger logger = LoggerFactory.getLogger(ProxyMessageDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ProxyMessage message = MessageCodec.INSTANCE.decodeProxyMessage(in);
        logger
                .trace("Decode proxy message, channel = {}, proxy message = {}",
                        ctx.channel().id().asLongText(),
                        message
                );
        out.add(message);
    }
}
