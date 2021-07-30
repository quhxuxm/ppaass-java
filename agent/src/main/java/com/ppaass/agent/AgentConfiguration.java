package com.ppaass.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Locale;

@ConfigurationProperties(prefix = "ppaass.agent")
@Component
public class AgentConfiguration {
    private final Logger logger = LoggerFactory.getLogger(AgentConfiguration.class);
    private static final String USER_CONFIGURATION_FILE_NAME = ".ppaass";
    private static final String USER_HOME_PROPERTY = "user.home";
    private String userToken;
    private int tcpPort;
    private String proxyHost;
    private int proxyPort;
    private Locale defaultLocal;
    private int agentTcpMasterThreadNumber;
    private int agentTcpWorkerThreadNumber;
    private int agentUdpThreadNumber;
    private int agentTcpSoBacklog;
    private int agentTcpSoLinger;
    private int agentTcpSoRcvbuf;
    private int agentTcpSoSndbuf;
    private int agentChannelAllIdleSeconds;
    private int proxyTcpThreadNumber;
    private int proxyTcpConnectionTimeout;
    private int proxyTcpSoLinger;
    private int proxyTcpSoRcvbuf;
    private int proxyTcpSoSndbuf;
    private boolean proxyTcpCompressEnable;
    private Resource agentPrivateKeyFile;
    private Resource proxyPublicKeyFile;
    private byte[] proxyPublicKey;
    private byte[] agentPrivateKey;
    private String agentInstanceId;
    private final ObjectMapper objectMapper;
    private String agentSourceAddress;
    private int delayCloseTimeMillis;
    private boolean withUi;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AgentDynamicConfiguration {
        @JsonProperty(required = false)
        private String userToken;
        @JsonProperty(required = false)
        private Integer tcpPort;
        @JsonProperty(required = false)
        private String proxyHost;
        @JsonProperty(required = false)
        private Integer proxyPort;
        @JsonProperty(required = false)
        private String agentInstanceId;

        public String getUserToken() {
            return userToken;
        }

        public void setUserToken(String userToken) {
            this.userToken = userToken;
        }

        public Integer getTcpPort() {
            return tcpPort;
        }

        public void setTcpPort(Integer tcpPort) {
            this.tcpPort = tcpPort;
        }

        public String getProxyHost() {
            return proxyHost;
        }

        public void setProxyHost(String proxyHost) {
            this.proxyHost = proxyHost;
        }

        public Integer getProxyPort() {
            return proxyPort;
        }

        public void setProxyPort(Integer proxyPort) {
            this.proxyPort = proxyPort;
        }

        public void setAgentInstanceId(String agentInstanceId) {
            this.agentInstanceId = agentInstanceId;
        }

        public String getAgentInstanceId() {
            return agentInstanceId;
        }
    }

    public AgentConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.agentInstanceId = UUIDUtil.INSTANCE.generateUuid();
        this.withUi = true;
    }

    @PostConstruct
    void init() {
        var userDirectory = System.getProperty(USER_HOME_PROPERTY);
        var agentDynamicConfigurationFilePath = Path.of(userDirectory, USER_CONFIGURATION_FILE_NAME);
        var agentDynamicConfigurationFile = agentDynamicConfigurationFilePath.toFile();
        if (agentDynamicConfigurationFile.exists()) {
            AgentDynamicConfiguration agentDynamicConfiguration =
                    null;
            try {
                agentDynamicConfiguration =
                        this.objectMapper.readValue(agentDynamicConfigurationFile,
                                AgentDynamicConfiguration.class);
            } catch (IOException e) {
                logger
                        .error("Fail to read agent configuration because of exception.", e);
                throw new PpaassException("Fail to read agent configuration because of exception.", e);
            }
            this.tcpPort = agentDynamicConfiguration.getTcpPort() == null ? this.tcpPort :
                    agentDynamicConfiguration.getTcpPort();
            this.proxyHost = agentDynamicConfiguration.getProxyHost() == null ? this.proxyHost :
                    agentDynamicConfiguration.getProxyHost();
            this.proxyPort = agentDynamicConfiguration.getProxyPort() == null ? this.proxyPort :
                    agentDynamicConfiguration.getProxyPort();
            this.userToken = agentDynamicConfiguration.getUserToken() == null ? this.userToken :
                    agentDynamicConfiguration.getUserToken();
            this.agentInstanceId = agentDynamicConfiguration.getAgentInstanceId() == null ? this.agentInstanceId :
                    agentDynamicConfiguration.getAgentInstanceId();
        }
        try {
            this.proxyPublicKey = proxyPublicKeyFile.getInputStream().readAllBytes();
        } catch (IOException e) {
            logger
                    .error("Fail to read proxy public key because of exception.", e);
            throw new PpaassException("Fail to read proxy public key because of exception.", e);
        }
        try {
            this.agentPrivateKey = agentPrivateKeyFile.getInputStream().readAllBytes();
        } catch (IOException e) {
            logger
                    .error("Fail to read agent private key because of exception.", e);
            throw new PpaassException("Fail to read agent private key because of exception.", e);
        }
        try {
            this.agentSourceAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger
                    .warn("Fail to get source host address because of exception, use agent instance id as the address",
                            e);
            this.agentSourceAddress = this.agentInstanceId;
        }
        CryptographyUtil.INSTANCE.init(this.proxyPublicKey, this.agentPrivateKey);
    }

    public String getUserToken() {
        return userToken;
    }

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Locale getDefaultLocal() {
        return defaultLocal;
    }

    public void setDefaultLocal(Locale defaultLocal) {
        this.defaultLocal = defaultLocal;
    }

    public int getAgentTcpMasterThreadNumber() {
        return agentTcpMasterThreadNumber;
    }

    public void setAgentTcpMasterThreadNumber(int agentTcpMasterThreadNumber) {
        this.agentTcpMasterThreadNumber = agentTcpMasterThreadNumber;
    }

    public int getAgentTcpWorkerThreadNumber() {
        return agentTcpWorkerThreadNumber;
    }

    public void setAgentTcpWorkerThreadNumber(int agentTcpWorkerThreadNumber) {
        this.agentTcpWorkerThreadNumber = agentTcpWorkerThreadNumber;
    }

    public int getAgentUdpThreadNumber() {
        return agentUdpThreadNumber;
    }

    public void setAgentUdpThreadNumber(int agentUdpThreadNumber) {
        this.agentUdpThreadNumber = agentUdpThreadNumber;
    }

    public int getAgentTcpSoBacklog() {
        return agentTcpSoBacklog;
    }

    public void setAgentTcpSoBacklog(int agentTcpSoBacklog) {
        this.agentTcpSoBacklog = agentTcpSoBacklog;
    }

    public int getAgentTcpSoLinger() {
        return agentTcpSoLinger;
    }

    public void setAgentTcpSoLinger(int agentTcpSoLinger) {
        this.agentTcpSoLinger = agentTcpSoLinger;
    }

    public int getAgentTcpSoRcvbuf() {
        return agentTcpSoRcvbuf;
    }

    public void setAgentTcpSoRcvbuf(int agentTcpSoRcvbuf) {
        this.agentTcpSoRcvbuf = agentTcpSoRcvbuf;
    }

    public int getAgentTcpSoSndbuf() {
        return agentTcpSoSndbuf;
    }

    public void setAgentTcpSoSndbuf(int agentTcpSoSndbuf) {
        this.agentTcpSoSndbuf = agentTcpSoSndbuf;
    }

    public int getProxyTcpThreadNumber() {
        return proxyTcpThreadNumber;
    }

    public void setProxyTcpThreadNumber(int proxyTcpThreadNumber) {
        this.proxyTcpThreadNumber = proxyTcpThreadNumber;
    }

    public int getProxyTcpConnectionTimeout() {
        return proxyTcpConnectionTimeout;
    }

    public void setProxyTcpConnectionTimeout(int proxyTcpConnectionTimeout) {
        this.proxyTcpConnectionTimeout = proxyTcpConnectionTimeout;
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

    public boolean isProxyTcpCompressEnable() {
        return proxyTcpCompressEnable;
    }

    public void setProxyTcpCompressEnable(boolean proxyTcpCompressEnable) {
        this.proxyTcpCompressEnable = proxyTcpCompressEnable;
    }

    public Resource getAgentPrivateKeyFile() {
        return agentPrivateKeyFile;
    }

    public void setAgentPrivateKeyFile(Resource agentPrivateKeyFile) {
        this.agentPrivateKeyFile = agentPrivateKeyFile;
    }

    public Resource getProxyPublicKeyFile() {
        return proxyPublicKeyFile;
    }

    public void setProxyPublicKeyFile(Resource proxyPublicKeyFile) {
        this.proxyPublicKeyFile = proxyPublicKeyFile;
    }

    public byte[] getProxyPublicKey() {
        return proxyPublicKey;
    }

    public byte[] getAgentPrivateKey() {
        return agentPrivateKey;
    }

    public String getAgentInstanceId() {
        return agentInstanceId;
    }

    public void setAgentInstanceId(String agentInstanceId) {
        this.agentInstanceId = agentInstanceId;
    }

    public String getAgentSourceAddress() {
        return agentSourceAddress;
    }

    public void setAgentChannelAllIdleSeconds(int agentChannelAllIdleSeconds) {
        this.agentChannelAllIdleSeconds = agentChannelAllIdleSeconds;
    }

    public int getAgentChannelAllIdleSeconds() {
        return agentChannelAllIdleSeconds;
    }

    public void setDelayCloseTimeMillis(int delayCloseTimeMillis) {
        this.delayCloseTimeMillis = delayCloseTimeMillis;
    }

    public int getDelayCloseTimeMillis() {
        return delayCloseTimeMillis;
    }

    public void setWithUi(boolean withUi) {
        this.withUi = withUi;
    }

    public boolean isWithUi() {
        return withUi;
    }

    public void save() {
        var userDirectory = System.getProperty(USER_HOME_PROPERTY);
        var agentDynamicConfigurationFilePath = Path.of(userDirectory, USER_CONFIGURATION_FILE_NAME);
        var agentDynamicConfigurationFile = agentDynamicConfigurationFilePath.toFile();
        if (agentDynamicConfigurationFile.exists()) {
            if (!agentDynamicConfigurationFile.delete()) {
                logger
                        .error("Fail to save agent configuration because of can not delete existing one.");
                throw new PpaassException("Fail to save agent configuration because of can not delete existing one.");
            }
        }
        try {
            if (!agentDynamicConfigurationFile.createNewFile()) {
                logger
                        .error("Fail to save agent configuration because of can not create new file.");
                throw new PpaassException("Fail to save agent configuration because of can not create new file.");
            }
        } catch (IOException e) {
            logger
                    .error("Fail to save agent configuration because of exception on create new file.",
                            e);
            throw new PpaassException("Fail to save agent configuration because of exception on create new file.", e);
        }
        var agentDynamicConfiguration = new AgentDynamicConfiguration();
        agentDynamicConfiguration.setUserToken(this.userToken);
        agentDynamicConfiguration.setTcpPort(this.tcpPort);
        agentDynamicConfiguration.setProxyHost(this.proxyHost);
        agentDynamicConfiguration.setProxyPort(this.proxyPort);
        agentDynamicConfiguration.setAgentInstanceId(this.agentInstanceId);
        try {
            this.objectMapper.writeValue(agentDynamicConfigurationFile, agentDynamicConfiguration);
        } catch (IOException e) {
            logger
                    .error("Fail to save agent configuration because of exception on write json to file.",
                            e);
            throw new PpaassException("Fail to save agent configuration because of exception on write json to file.",
                    e);
        }
    }
}
