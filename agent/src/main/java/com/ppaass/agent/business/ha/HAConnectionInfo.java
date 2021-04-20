package com.ppaass.agent.business.ha;

import io.netty.channel.Channel;

class HAConnectionInfo {
    private boolean onConnecting;
    private final String targetHost;
    private final int targetPort;
    private final boolean isHttps;
    private String userToken;
    private Channel proxyChannel;
    private Channel agentChannel;
    private Object httpMessageCarriedOnConnectTime;
    private final String uri;

    public HAConnectionInfo(String targetHost, int targetPort, boolean isHttps, String uri) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.isHttps = isHttps;
        this.uri = uri;
        this.onConnecting = false;
    }

    public void setOnConnecting(boolean onConnecting) {
        this.onConnecting = onConnecting;
    }

    public boolean isOnConnecting() {
        return onConnecting;
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

    public String getUserToken() {
        return userToken;
    }

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }

    public Channel getProxyChannel() {
        return proxyChannel;
    }

    public void setProxyChannel(Channel proxyChannel) {
        this.proxyChannel = proxyChannel;
    }

    public Channel getAgentChannel() {
        return agentChannel;
    }

    public void setAgentChannel(Channel agentChannel) {
        this.agentChannel = agentChannel;
    }

    public Object getHttpMessageCarriedOnConnectTime() {
        return httpMessageCarriedOnConnectTime;
    }

    public void setHttpMessageCarriedOnConnectTime(Object httpMessageCarriedOnConnectTime) {
        this.httpMessageCarriedOnConnectTime = httpMessageCarriedOnConnectTime;
    }

    public String getUri() {
        return uri;
    }
}
