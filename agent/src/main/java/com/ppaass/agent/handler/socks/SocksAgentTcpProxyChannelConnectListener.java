package com.ppaass.agent.handler.socks;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

class SocksAgentTcpProxyChannelConnectListener implements ChannelFutureListener {
    private int failureTimes;

    public SocksAgentTcpProxyChannelConnectListener() {
        this.failureTimes = 0;
    }

    @Override
    public void operationComplete(ChannelFuture proxyChannelFuture) throws Exception {
    }
}
