package com.ppaass.agent.handler.http;

import com.ppaass.agent.handler.http.bo.HttpConnectionInfo;
import io.netty.util.AttributeKey;

interface IHttpAgentConstant {
    AttributeKey<HttpConnectionInfo> HTTP_CONNECTION_INFO =
            AttributeKey.valueOf("HTTP_CONNECTION_INFO");
    String CONNECTION_ESTABLISHED = "Connection Established";
}
