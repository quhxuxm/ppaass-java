package com.ppaass.common.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class PrintExceptionHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(PrintExceptionHandler.class);
    public static final PrintExceptionHandler INSTANCE = new PrintExceptionHandler();

    private PrintExceptionHandler() {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger
                .trace("Exception in the channel pipeline, channel = {}",
                        ctx.channel().id(), cause
                );
        ctx.fireExceptionCaught(cause);
    }
}


