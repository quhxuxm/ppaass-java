package com.ppaass.agent.handler.http;

import com.ppaass.agent.handler.http.bo.HttpConnectionInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AttributeKey;

import java.nio.charset.StandardCharsets;

interface IHttpAgentConstant {
    AttributeKey<HttpConnectionInfo> HTTP_CONNECTION_INFO =
            AttributeKey.valueOf("HTTP_CONNECTION_INFO");
    ByteBuf CONNECTION_ESTABLISHED = Unpooled.wrappedBuffer("Connection Established".getBytes(StandardCharsets.UTF_8));
}
