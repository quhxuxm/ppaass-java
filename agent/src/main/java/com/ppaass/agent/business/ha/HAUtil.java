package com.ppaass.agent.business.ha;

import com.ppaass.common.log.PpaassLogger;
import com.ppaass.protocol.common.util.UUIDUtil;
import com.ppaass.protocol.vpn.message.AgentMessage;
import com.ppaass.protocol.vpn.message.AgentMessageBody;
import com.ppaass.protocol.vpn.message.AgentMessageBodyType;
import com.ppaass.protocol.vpn.message.EncryptionType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import org.springframework.web.util.UriComponentsBuilder;

class HAUtil {
    private static final String HTTP_SCHEMA = "http://";
    private static final String HTTPS_SCHEMA = "https://";
    private static final String SCHEMA_AND_HOST_SEP = "://";
    private static final String HOST_NAME_AND_PORT_SEP = ":";
    private static final String SLASH = "/";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    static final HAUtil INSTANCE = new HAUtil();

    private HAUtil() {
    }

    public HAConnectionInfo parseConnectionInfo(String uri) {
        if (uri.startsWith(HTTP_SCHEMA)) {
            var uriComponentsBuilder = UriComponentsBuilder.fromUriString(uri);
            var uriComponents = uriComponentsBuilder.build();
            var port = uriComponents.getPort();
            if (port < 0) {
                port = DEFAULT_HTTP_PORT;
            }
            var targetHost = uriComponents.getHost();
            if (targetHost == null) {
                targetHost = "";
            }
            return new HAConnectionInfo(
                    uri,
                    targetHost,
                    port,
                    false
            );
        }
        if (uri.startsWith(HTTPS_SCHEMA)) {
            var uriComponentsBuilder = UriComponentsBuilder.fromUriString(uri);
            var uriComponents = uriComponentsBuilder.build();
            var port = uriComponents.getPort();
            if (port < 0) {
                port = DEFAULT_HTTPS_PORT;
            }
            var targetHost = uriComponents.getHost();
            if (targetHost == null) {
                targetHost = "";
            }
            return new HAConnectionInfo(
                    uri,
                    targetHost,
                    port,
                    true
            );
        }
        //For CONNECT method, only HTTPS will do this method.
        var schemaAndHostNameSepIndex = uri.indexOf(
                SCHEMA_AND_HOST_SEP);
        var hostNameAndPort = uri;
        if (schemaAndHostNameSepIndex >= 0) {
            hostNameAndPort = uri.substring(
                    schemaAndHostNameSepIndex + SCHEMA_AND_HOST_SEP.length());
        }
        if (hostNameAndPort.contains(SLASH)) {
            return null;
        }
        var hostNameAndPortParts = hostNameAndPort.split(
                HOST_NAME_AND_PORT_SEP);
        var hostName = hostNameAndPortParts[0];
        var port = DEFAULT_HTTPS_PORT;
        if (hostNameAndPortParts.length > 1) {
            try {
                port = Integer.parseInt(hostNameAndPortParts[1]);
            } catch (NumberFormatException e) {
                PpaassLogger.INSTANCE.error(HAUtil.class,
                        () -> "Fail to parse port from request uri, uri = {}",
                        () -> new Object[]{uri});
            }
        }
        return new HAConnectionInfo(
                uri,
                hostName,
                port,
                true
        );
    }

    public void writeAgentMessageToProxy(AgentMessageBodyType bodyType, String userToken, String agentInstanceId,
                                         Channel proxyChannel, Object input,
                                         String sourceHost, int sourcePort,
                                         String targetHost, int targetPort,
                                         String agentChannelId,
                                         ChannelFutureListener writeCallback) {
        byte[] data = null;
        if (input == null) {
            data = new byte[]{};
        } else {
            if (input instanceof HttpRequest) {
                var tempChannel = new EmbeddedChannel(new HttpRequestEncoder());
                tempChannel.writeOutbound(input);
                ByteBuf httpRequestByteBuf = tempChannel.readOutbound();
                data = ByteBufUtil.getBytes(httpRequestByteBuf);
            } else {
                if (input instanceof ByteBuf) {
                    data = ByteBufUtil.getBytes((ByteBuf) input);
                } else {
                    data = new byte[]{};
                }
            }
        }
        var agentMessageBody =
                new AgentMessageBody(
                        UUIDUtil.INSTANCE.generateUuid(),
                        agentInstanceId,
                        userToken,
                        sourceHost,
                        sourcePort,
                        targetHost,
                        targetPort,
                        bodyType,
                        agentChannelId,
                        null,
                        data);
        var agentMessage =
                new AgentMessage(
                        UUIDUtil.INSTANCE.generateUuidInBytes(),
                        EncryptionType.choose(),
                        agentMessageBody);
        var writeResultFuture = proxyChannel.writeAndFlush(agentMessage);
        if (writeCallback != null) {
            writeResultFuture.addListener(writeCallback);
        }
    }
}
