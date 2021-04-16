package com.ppaass.proxy;

import com.ppaass.common.log.PpaassLogger;
import io.netty.bootstrap.ServerBootstrap;
import org.springframework.stereotype.Service;

@Service
public class Proxy {
    private final ProxyConfiguration proxyConfiguration;
    private final ServerBootstrap proxyServerBootstrap;

    public Proxy(ProxyConfiguration proxyConfiguration, ServerBootstrap proxyServerBootstrap) {
        this.proxyConfiguration = proxyConfiguration;
        this.proxyServerBootstrap = proxyServerBootstrap;
    }

    public String getInstanceId() {
        return this.proxyConfiguration.getProxyInstanceId();
    }

    public void start() {
        try {
            proxyServerBootstrap.bind(proxyConfiguration.getProxyTcpServerPort()).sync();
        } catch (Exception e) {
            PpaassLogger.INSTANCE.error(Proxy.class, () -> "Fail to start ppaass tcp proxy because of exception.",
                    () -> new Object[]{e});
            System.exit(1);
        }
    }

    public void stop() {
        proxyServerBootstrap.config().group().shutdownGracefully();
        proxyServerBootstrap.config().childGroup().shutdownGracefully();
    }
}
