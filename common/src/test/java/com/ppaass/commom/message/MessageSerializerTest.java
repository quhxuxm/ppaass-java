package com.ppaass.commom.message;

import com.ppaass.common.message.MessageSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.io.FileInputStream;
import java.io.IOException;

public class MessageSerializerTest {
    public static void main(String[] args) throws IOException {
        FileInputStream fileInputStream = new FileInputStream("D:\\ppaass_message");
        FileInputStream proxyPublicKeyStream =
                new FileInputStream("D:\\Git\\ppaass\\agent\\src\\main\\resources\\security\\agentPrivateKey");
        byte[] allBytes = fileInputStream.readAllBytes();
        EmbeddedChannel embeddedChannel =
                new EmbeddedChannel(
                        new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(allBytes));
        ByteBuf proxyMessageBuf = embeddedChannel.readInbound();
        var message = MessageSerializer.INSTANCE
                .decodeProxyMessage(proxyMessageBuf, proxyPublicKeyStream.readAllBytes());
        System.out.println(message.getBody().getBodyType());
    }
}
