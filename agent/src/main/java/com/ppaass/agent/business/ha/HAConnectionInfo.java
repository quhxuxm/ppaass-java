package com.ppaass.agent.business.ha;

import io.netty.channel.Channel;

class HAConnectionInfo {
    private final String targetHost;
    private final int targetPort;
    private final String uri;
    private final boolean isHttps;
    private Object httpMessageCarriedOnConnectTime;
    private Channel proxyChannel;

    public HAConnectionInfo(String uri, String targetHost, int targetPort, boolean isHttps) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.isHttps = isHttps;
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public void setProxyChannel(Channel proxyChannel) {
        this.proxyChannel = proxyChannel;
    }

    public Channel getProxyChannel() {
        return proxyChannel;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public boolean isHttps() {
        return isHttps;
    }

    public Object getHttpMessageCarriedOnConnectTime() {
        return httpMessageCarriedOnConnectTime;
    }

    public void setHttpMessageCarriedOnConnectTime(Object httpMessageCarriedOnConnectTime) {
        this.httpMessageCarriedOnConnectTime = httpMessageCarriedOnConnectTime;
    }
}
