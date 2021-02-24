package com.ppaass.proxy;

import io.netty.bootstrap.ServerBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Proxy {
    private static final Logger logger = LoggerFactory.getLogger(Proxy.class);
    private final ProxyConfiguration proxyConfiguration;
    private final ServerBootstrap proxyServerBootstrap;

    public Proxy(ProxyConfiguration proxyConfiguration, ServerBootstrap proxyServerBootstrap) {
        this.proxyConfiguration = proxyConfiguration;
        this.proxyServerBootstrap = proxyServerBootstrap;
    }

    public void start() {
        try {
            proxyServerBootstrap.bind(proxyConfiguration.getProxyTcpServerPort()).sync();
        } catch (Exception e) {
            logger.error("Fail to start ppaass tcp proxy because of exception.", e);
            System.exit(1);
        }
    }

    public void stop() {
        proxyServerBootstrap.config().group().shutdownGracefully();
        proxyServerBootstrap.config().childGroup().shutdownGracefully();
    }
}
