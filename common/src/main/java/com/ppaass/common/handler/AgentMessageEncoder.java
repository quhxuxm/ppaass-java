package com.ppaass.common.handler;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.AgentMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class AgentMessageEncoder extends MessageToByteEncoder<AgentMessage> {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();

    @Override
    protected void encode(ChannelHandlerContext ctx, AgentMessage msg, ByteBuf out) throws Exception {
        MessageCodec.INSTANCE.encodeAgentMessage(msg, out);
        logger
                .trace(AgentMessageEncoder.class, () -> "Encode agent message, channel = {}, agent message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                msg
                        });
    }
}
