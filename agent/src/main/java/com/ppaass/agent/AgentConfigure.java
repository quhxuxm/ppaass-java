package com.ppaass.agent;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfigure {
    @Bean
    public EventLoopGroup proxyTcpLoopGroup(AgentConfiguration agentConfiguration) {
        return new NioEventLoopGroup(agentConfiguration.getProxyTcpThreadNumber());
    }
}
