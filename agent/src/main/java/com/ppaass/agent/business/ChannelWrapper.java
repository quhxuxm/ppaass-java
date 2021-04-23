package com.ppaass.agent.business;

import io.netty.channel.Channel;

public class ChannelWrapper {
    private final Channel channel;
    private long closeTime;
    private boolean closed;

    public ChannelWrapper(Channel channel) {
        this.channel = channel;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        this.closeTime = System.currentTimeMillis();
        this.closed = true;
        this.channel.close();
    }
}
