package com.ppaass.agent.handler;

import com.ppaass.agent.IAgentConst;
import com.ppaass.common.handler.PrintExceptionHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.springframework.stereotype.Service;

@ChannelHandler.Sharable
@Service
public class AgentChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final DetectProtocolHandler detectProtocolHandler;
    private final PrintExceptionHandler printExceptionHandler;

    public AgentChannelInitializer(DetectProtocolHandler detectProtocolHandler,
                                   PrintExceptionHandler printExceptionHandler) {
        this.detectProtocolHandler = detectProtocolHandler;
        this.printExceptionHandler = printExceptionHandler;
    }

    public void initChannel(SocketChannel agentChannel) {
        agentChannel.pipeline().addLast(detectProtocolHandler);
        agentChannel.pipeline().addLast(IAgentConst.LAST_INBOUND_HANDLER, printExceptionHandler);
    }
}
