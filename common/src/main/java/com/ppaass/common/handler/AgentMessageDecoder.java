package com.ppaass.common.handler;

import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.AgentMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AgentMessageDecoder extends ByteToMessageDecoder {
    private final Logger logger = LoggerFactory.getLogger(AgentMessageDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        AgentMessage message = MessageCodec.INSTANCE.decodeAgentMessage(in);
        logger
                .trace("Decode agent message, channel = {}, agent message = {}",
                        ctx.channel().id().asLongText(),
                        message);
        out.add(message);
    }
}
