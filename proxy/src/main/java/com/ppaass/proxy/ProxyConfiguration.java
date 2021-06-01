package com.ppaass.proxy;

import com.ppaass.common.exception.PpaassException;
import com.ppaass.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.cryptography.CryptographyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@ConfigurationProperties(prefix = "ppaass.proxy")
@Component
public class ProxyConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(ProxyConfiguration.class);
    private String proxyInstanceId;
    private int proxyTcpServerPort;
    private int proxyTcpMasterThreadNumber;
    private int proxyTcpWorkerThreadNumber;
    private int proxyTcpSoLinger;
    private int proxyTcpSoRcvbuf;
    private int proxyTcpSoSndbuf;
    private int proxyTcpSoBacklog;
    private int proxyTcpReceiveDataAverageBufferMinSize;
    private int proxyTcpReceiveDataAverageBufferInitialSize;
    private int proxyTcpReceiveDataAverageBufferMaxSize;
    private long proxyTcpTrafficShapingWriteChannelLimit;
    private long proxyTcpTrafficShapingReadChannelLimit;
    private long proxyTcpTrafficShapingCheckInterval;
    private int proxyTcpWriteSpinCount;
    private int proxyTcpChannelReadIdleSeconds;
    private int proxyTcpChannelWriteIdleSeconds;
    private int proxyTcpChannelAllIdleSeconds;
    private int proxyTcpWriteBufferWaterMarkLow;
    private int proxyTcpWriteBufferWaterMarkHigh;
    private boolean proxyTcpCompressEnable;
    private int targetTcpThreadNumber;
    private int targetTcpConnectionTimeout;
    private int targetTcpSoLinger;
    private int targetTcpSoRcvbuf;
    private int targetTcpSoSndbuf;
    private int targetUdpReceiveTimeout;
    private int targetTcpWriteSpinCount;
    private int targetTcpWriteBufferWaterMarkLow;
    private int targetTcpWriteBufferWaterMarkHigh;
    private int targetTcpReceiveDataAverageBufferMinSize;
    private int targetTcpReceiveDataAverageBufferInitialSize;
    private int targetTcpReceiveDataAverageBufferMaxSize;
    private long targetTcpTrafficShapingWriteChannelLimit;
    private long targetTcpTrafficShapingReadChannelLimit;
    private long targetTcpTrafficShapingCheckInterval;
    private int targetTcpChannelReadIdleSeconds;
    private int targetTcpChannelWriteIdleSeconds;
    private int targetTcpChannelAllIdleSeconds;
    private Resource proxyPrivateKeyFile;
    private Resource agentPublicKeyFile;
    private byte[] proxyPrivateKey;
    private byte[] agentPublicKey;
    private int delayCloseTimeSeconds;

    public ProxyConfiguration() {
        this.proxyInstanceId = UUIDUtil.INSTANCE.generateUuid();
    }

    public static Logger getLogger() {
        return logger;
    }

    public int getProxyTcpServerPort() {
        return proxyTcpServerPort;
    }

    public void setProxyTcpServerPort(int proxyTcpServerPort) {
        this.proxyTcpServerPort = proxyTcpServerPort;
    }

    public int getProxyTcpMasterThreadNumber() {
        return proxyTcpMasterThreadNumber;
    }

    public void setProxyTcpMasterThreadNumber(int proxyTcpMasterThreadNumber) {
        this.proxyTcpMasterThreadNumber = proxyTcpMasterThreadNumber;
    }

    public int getProxyTcpWorkerThreadNumber() {
        return proxyTcpWorkerThreadNumber;
    }

    public void setProxyTcpWorkerThreadNumber(int proxyTcpWorkerThreadNumber) {
        this.proxyTcpWorkerThreadNumber = proxyTcpWorkerThreadNumber;
    }

    public int getProxyTcpSoLinger() {
        return proxyTcpSoLinger;
    }

    public void setProxyTcpSoLinger(int proxyTcpSoLinger) {
        this.proxyTcpSoLinger = proxyTcpSoLinger;
    }

    public int getProxyTcpSoRcvbuf() {
        return proxyTcpSoRcvbuf;
    }

    public void setProxyTcpSoRcvbuf(int proxyTcpSoRcvbuf) {
        this.proxyTcpSoRcvbuf = proxyTcpSoRcvbuf;
    }

    public int getProxyTcpSoSndbuf() {
        return proxyTcpSoSndbuf;
    }

    public void setProxyTcpSoSndbuf(int proxyTcpSoSndbuf) {
        this.proxyTcpSoSndbuf = proxyTcpSoSndbuf;
    }

    public int getProxyTcpSoBacklog() {
        return proxyTcpSoBacklog;
    }

    public void setProxyTcpSoBacklog(int proxyTcpSoBacklog) {
        this.proxyTcpSoBacklog = proxyTcpSoBacklog;
    }

    public int getProxyTcpReceiveDataAverageBufferMinSize() {
        return proxyTcpReceiveDataAverageBufferMinSize;
    }

    public void setProxyTcpReceiveDataAverageBufferMinSize(int proxyTcpReceiveDataAverageBufferMinSize) {
        this.proxyTcpReceiveDataAverageBufferMinSize = proxyTcpReceiveDataAverageBufferMinSize;
    }

    public int getProxyTcpReceiveDataAverageBufferInitialSize() {
        return proxyTcpReceiveDataAverageBufferInitialSize;
    }

    public void setProxyTcpReceiveDataAverageBufferInitialSize(int proxyTcpReceiveDataAverageBufferInitialSize) {
        this.proxyTcpReceiveDataAverageBufferInitialSize = proxyTcpReceiveDataAverageBufferInitialSize;
    }

    public int getProxyTcpReceiveDataAverageBufferMaxSize() {
        return proxyTcpReceiveDataAverageBufferMaxSize;
    }

    public void setProxyTcpReceiveDataAverageBufferMaxSize(int proxyTcpReceiveDataAverageBufferMaxSize) {
        this.proxyTcpReceiveDataAverageBufferMaxSize = proxyTcpReceiveDataAverageBufferMaxSize;
    }

    public long getProxyTcpTrafficShapingWriteChannelLimit() {
        return proxyTcpTrafficShapingWriteChannelLimit;
    }

    public void setProxyTcpTrafficShapingWriteChannelLimit(long proxyTcpTrafficShapingWriteChannelLimit) {
        this.proxyTcpTrafficShapingWriteChannelLimit = proxyTcpTrafficShapingWriteChannelLimit;
    }

    public long getProxyTcpTrafficShapingReadChannelLimit() {
        return proxyTcpTrafficShapingReadChannelLimit;
    }

    public void setProxyTcpTrafficShapingReadChannelLimit(long proxyTcpTrafficShapingReadChannelLimit) {
        this.proxyTcpTrafficShapingReadChannelLimit = proxyTcpTrafficShapingReadChannelLimit;
    }

    public long getProxyTcpTrafficShapingCheckInterval() {
        return proxyTcpTrafficShapingCheckInterval;
    }

    public void setProxyTcpTrafficShapingCheckInterval(long proxyTcpTrafficShapingCheckInterval) {
        this.proxyTcpTrafficShapingCheckInterval = proxyTcpTrafficShapingCheckInterval;
    }

    public int getProxyTcpWriteSpinCount() {
        return proxyTcpWriteSpinCount;
    }

    public void setProxyTcpWriteSpinCount(int proxyTcpWriteSpinCount) {
        this.proxyTcpWriteSpinCount = proxyTcpWriteSpinCount;
    }

    public int getProxyTcpChannelReadIdleSeconds() {
        return proxyTcpChannelReadIdleSeconds;
    }

    public void setProxyTcpChannelReadIdleSeconds(int proxyTcpChannelReadIdleSeconds) {
        this.proxyTcpChannelReadIdleSeconds = proxyTcpChannelReadIdleSeconds;
    }

    public int getProxyTcpChannelWriteIdleSeconds() {
        return proxyTcpChannelWriteIdleSeconds;
    }

    public void setProxyTcpChannelWriteIdleSeconds(int proxyTcpChannelWriteIdleSeconds) {
        this.proxyTcpChannelWriteIdleSeconds = proxyTcpChannelWriteIdleSeconds;
    }

    public int getProxyTcpChannelAllIdleSeconds() {
        return proxyTcpChannelAllIdleSeconds;
    }

    public void setProxyTcpChannelAllIdleSeconds(int proxyTcpChannelAllIdleSeconds) {
        this.proxyTcpChannelAllIdleSeconds = proxyTcpChannelAllIdleSeconds;
    }

    public boolean isProxyTcpCompressEnable() {
        return proxyTcpCompressEnable;
    }

    public void setProxyTcpCompressEnable(boolean proxyTcpCompressEnable) {
        this.proxyTcpCompressEnable = proxyTcpCompressEnable;
    }

    public int getTargetTcpThreadNumber() {
        return targetTcpThreadNumber;
    }

    public void setTargetTcpThreadNumber(int targetTcpThreadNumber) {
        this.targetTcpThreadNumber = targetTcpThreadNumber;
    }

    public int getTargetTcpConnectionTimeout() {
        return targetTcpConnectionTimeout;
    }

    public void setTargetTcpConnectionTimeout(int targetTcpConnectionTimeout) {
        this.targetTcpConnectionTimeout = targetTcpConnectionTimeout;
    }

    public int getTargetTcpSoLinger() {
        return targetTcpSoLinger;
    }

    public void setTargetTcpSoLinger(int targetTcpSoLinger) {
        this.targetTcpSoLinger = targetTcpSoLinger;
    }

    public int getTargetTcpSoRcvbuf() {
        return targetTcpSoRcvbuf;
    }

    public void setTargetTcpSoRcvbuf(int targetTcpSoRcvbuf) {
        this.targetTcpSoRcvbuf = targetTcpSoRcvbuf;
    }

    public int getTargetTcpSoSndbuf() {
        return targetTcpSoSndbuf;
    }

    public void setTargetTcpSoSndbuf(int targetTcpSoSndbuf) {
        this.targetTcpSoSndbuf = targetTcpSoSndbuf;
    }

    public int getTargetTcpWriteSpinCount() {
        return targetTcpWriteSpinCount;
    }

    public void setTargetTcpWriteSpinCount(int targetTcpWriteSpinCount) {
        this.targetTcpWriteSpinCount = targetTcpWriteSpinCount;
    }

    public int getTargetTcpWriteBufferWaterMarkLow() {
        return targetTcpWriteBufferWaterMarkLow;
    }

    public void setTargetTcpWriteBufferWaterMarkLow(int targetTcpWriteBufferWaterMarkLow) {
        this.targetTcpWriteBufferWaterMarkLow = targetTcpWriteBufferWaterMarkLow;
    }

    public int getTargetTcpWriteBufferWaterMarkHigh() {
        return targetTcpWriteBufferWaterMarkHigh;
    }

    public void setTargetTcpWriteBufferWaterMarkHigh(int targetTcpWriteBufferWaterMarkHigh) {
        this.targetTcpWriteBufferWaterMarkHigh = targetTcpWriteBufferWaterMarkHigh;
    }

    public int getTargetTcpReceiveDataAverageBufferMinSize() {
        return targetTcpReceiveDataAverageBufferMinSize;
    }

    public void setTargetTcpReceiveDataAverageBufferMinSize(int targetTcpReceiveDataAverageBufferMinSize) {
        this.targetTcpReceiveDataAverageBufferMinSize = targetTcpReceiveDataAverageBufferMinSize;
    }

    public int getTargetTcpReceiveDataAverageBufferInitialSize() {
        return targetTcpReceiveDataAverageBufferInitialSize;
    }

    public void setTargetTcpReceiveDataAverageBufferInitialSize(int targetTcpReceiveDataAverageBufferInitialSize) {
        this.targetTcpReceiveDataAverageBufferInitialSize = targetTcpReceiveDataAverageBufferInitialSize;
    }

    public int getTargetTcpReceiveDataAverageBufferMaxSize() {
        return targetTcpReceiveDataAverageBufferMaxSize;
    }

    public void setTargetTcpReceiveDataAverageBufferMaxSize(int targetTcpReceiveDataAverageBufferMaxSize) {
        this.targetTcpReceiveDataAverageBufferMaxSize = targetTcpReceiveDataAverageBufferMaxSize;
    }

    public long getTargetTcpTrafficShapingWriteChannelLimit() {
        return targetTcpTrafficShapingWriteChannelLimit;
    }

    public void setTargetTcpTrafficShapingWriteChannelLimit(long targetTcpTrafficShapingWriteChannelLimit) {
        this.targetTcpTrafficShapingWriteChannelLimit = targetTcpTrafficShapingWriteChannelLimit;
    }

    public long getTargetTcpTrafficShapingReadChannelLimit() {
        return targetTcpTrafficShapingReadChannelLimit;
    }

    public void setTargetTcpTrafficShapingReadChannelLimit(long targetTcpTrafficShapingReadChannelLimit) {
        this.targetTcpTrafficShapingReadChannelLimit = targetTcpTrafficShapingReadChannelLimit;
    }

    public long getTargetTcpTrafficShapingCheckInterval() {
        return targetTcpTrafficShapingCheckInterval;
    }

    public void setTargetTcpTrafficShapingCheckInterval(long targetTcpTrafficShapingCheckInterval) {
        this.targetTcpTrafficShapingCheckInterval = targetTcpTrafficShapingCheckInterval;
    }

    public int getTargetTcpChannelReadIdleSeconds() {
        return targetTcpChannelReadIdleSeconds;
    }

    public void setTargetTcpChannelReadIdleSeconds(int targetTcpChannelReadIdleSeconds) {
        this.targetTcpChannelReadIdleSeconds = targetTcpChannelReadIdleSeconds;
    }

    public int getTargetTcpChannelWriteIdleSeconds() {
        return targetTcpChannelWriteIdleSeconds;
    }

    public void setTargetTcpChannelWriteIdleSeconds(int targetTcpChannelWriteIdleSeconds) {
        this.targetTcpChannelWriteIdleSeconds = targetTcpChannelWriteIdleSeconds;
    }

    public int getTargetTcpChannelAllIdleSeconds() {
        return targetTcpChannelAllIdleSeconds;
    }

    public void setTargetTcpChannelAllIdleSeconds(int targetTcpChannelAllIdleSeconds) {
        this.targetTcpChannelAllIdleSeconds = targetTcpChannelAllIdleSeconds;
    }

    public Resource getProxyPrivateKeyFile() {
        return proxyPrivateKeyFile;
    }

    public void setProxyPrivateKeyFile(Resource proxyPrivateKeyFile) {
        this.proxyPrivateKeyFile = proxyPrivateKeyFile;
    }

    public Resource getAgentPublicKeyFile() {
        return agentPublicKeyFile;
    }

    public void setAgentPublicKeyFile(Resource agentPublicKeyFile) {
        this.agentPublicKeyFile = agentPublicKeyFile;
    }

    public byte[] getAgentPublicKey() {
        return agentPublicKey;
    }

    public byte[] getProxyPrivateKey() {
        return proxyPrivateKey;
    }

    public void setProxyInstanceId(String proxyInstanceId) {
        this.proxyInstanceId = proxyInstanceId;
    }

    public String getProxyInstanceId() {
        return proxyInstanceId;
    }

    public void setTargetUdpReceiveTimeout(int targetUdpReceiveTimeout) {
        this.targetUdpReceiveTimeout = targetUdpReceiveTimeout;
    }

    public int getTargetUdpReceiveTimeout() {
        return targetUdpReceiveTimeout;
    }

    public void setDelayCloseTimeSeconds(int delayCloseTimeSeconds) {
        this.delayCloseTimeSeconds = delayCloseTimeSeconds;
    }

    public int getDelayCloseTimeSeconds() {
        return delayCloseTimeSeconds;
    }

    public void setProxyTcpWriteBufferWaterMarkHigh(int proxyTcpWriteBufferWaterMarkHigh) {
        this.proxyTcpWriteBufferWaterMarkHigh = proxyTcpWriteBufferWaterMarkHigh;
    }

    public int getProxyTcpWriteBufferWaterMarkHigh() {
        return proxyTcpWriteBufferWaterMarkHigh;
    }

    public void setProxyTcpWriteBufferWaterMarkLow(int proxyTcpWriteBufferWaterMarkLow) {
        this.proxyTcpWriteBufferWaterMarkLow = proxyTcpWriteBufferWaterMarkLow;
    }

    public int getProxyTcpWriteBufferWaterMarkLow() {
        return proxyTcpWriteBufferWaterMarkLow;
    }

    @PostConstruct
    void init() {
        try {
            proxyPrivateKey = proxyPrivateKeyFile.getInputStream().readAllBytes();
        } catch (IOException e) {
            logger.error("Fail to read proxy public key because of exception.", e);
            throw new PpaassException("Fail to read proxy public key because of exception.", e);
        }
        try {
            agentPublicKey = agentPublicKeyFile.getInputStream().readAllBytes();
        } catch (IOException e) {
            logger.error("Fail to read agent private key because of exception.", e);
            throw new PpaassException("Fail to read agent private key because of exception.", e);
        }
        CryptographyUtil.INSTANCE.init(agentPublicKey, proxyPrivateKey);
    }
}
