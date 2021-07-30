package com.ppaass.common.handler;

import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.AgentMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentMessageEncoder extends MessageToByteEncoder<AgentMessage> {
    private final Logger logger = LoggerFactory.getLogger(AgentMessageEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, AgentMessage msg, ByteBuf out) throws Exception {
        MessageCodec.INSTANCE.encodeAgentMessage(msg, out);
        logger
                .trace("Encode agent message, channel = {}, agent message = {}",
                        ctx.channel().id().asLongText(),
                        msg
                );
    }
}
