package com.ppaass.agent.handler.http.bo;

import io.netty.channel.Channel;

public class HttpConnectionInfo {
    private final String targetHost;
    private final int targetPort;
    private final boolean isHttps;
    private String userToken;
    private Channel proxyChannel;
    private Channel agentChannel;
    private boolean isKeepAlive;
    private Object httpMessageCarriedOnConnectTime;

    public HttpConnectionInfo(String targetHost, int targetPort, boolean isHttps) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.isHttps = isHttps;
        this.isKeepAlive = true;
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

    public boolean isKeepAlive() {
        return isKeepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        isKeepAlive = keepAlive;
    }

    public Object getHttpMessageCarriedOnConnectTime() {
        return httpMessageCarriedOnConnectTime;
    }

    public void setHttpMessageCarriedOnConnectTime(Object httpMessageCarriedOnConnectTime) {
        this.httpMessageCarriedOnConnectTime = httpMessageCarriedOnConnectTime;
    }
}
