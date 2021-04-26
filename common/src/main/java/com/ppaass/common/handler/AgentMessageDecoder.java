package com.ppaass.common.handler;

import com.ppaass.common.log.IPpaassLogger;
import com.ppaass.common.log.PpaassLoggerFactory;
import com.ppaass.protocol.vpn.codec.MessageCodec;
import com.ppaass.protocol.vpn.message.AgentMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class AgentMessageDecoder extends ByteToMessageDecoder {
    private final IPpaassLogger logger = PpaassLoggerFactory.INSTANCE.getLogger();
    private final byte[] proxyPrivateKey;

    public AgentMessageDecoder(byte[] proxyPrivateKey) {
        this.proxyPrivateKey = proxyPrivateKey;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        AgentMessage message = MessageCodec.INSTANCE.decodeAgentMessage(in, this.proxyPrivateKey);
        logger
                .trace(AgentMessageDecoder.class, () -> "Decode agent message, channel = {}, agent message = {}",
                        () -> new Object[]{
                                ctx.channel().id().asLongText(),
                                message
                        });
        out.add(message);
    }
}
