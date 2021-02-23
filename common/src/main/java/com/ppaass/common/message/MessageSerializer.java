package com.ppaass.common.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppaass.common.cryptography.CryptographyUtil;
import com.ppaass.common.cryptography.EncryptionType;
import com.ppaass.common.exception.PpaassException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MessageSerializer {
    private static final byte[] MAGIC_CODE = "__PPAASS__".getBytes(StandardCharsets.UTF_8);
    private static final Logger logger = LoggerFactory.getLogger(MessageSerializer.class);
    public static final MessageSerializer INSTANCE = new MessageSerializer();
    public static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

    private MessageSerializer() {
    }

    private AgentMessageBodyType parseAgentMessageBodyType(byte b) {
        for (var e : AgentMessageBodyType.values()) {
            if (e.value() == b) {
                return e;
            }
        }
        return null;
    }

    private ProxyMessageBodyType parseProxyMessageBodyType(byte b) {
        for (var e : ProxyMessageBodyType.values()) {
            if (e.value() == b) {
                return e;
            }
        }
        return null;
    }

    private EncryptionType parseEncryptionType(byte b) {
        for (var e : EncryptionType.values()) {
            if (e.value() == b) {
                return e;
            }
        }
        return null;
    }

    private byte[] encryptMessageBody(byte[] messageBodyByteArrayBeforeEncrypt,
                                      EncryptionType messageBodyBodyEncryptionType,
                                      byte[] messageBodyEncryptionToken) {
        return switch (messageBodyBodyEncryptionType) {
            case AES -> CryptographyUtil.INSTANCE.aesEncrypt(messageBodyEncryptionToken,
                    messageBodyByteArrayBeforeEncrypt);
            case BLOWFISH -> CryptographyUtil.INSTANCE.blowfishEncrypt(messageBodyEncryptionToken,
                    messageBodyByteArrayBeforeEncrypt);
        };
    }

    private byte[] decryptMessageBody(byte[] messageBodyByteArrayBeforeDecrypt,
                                      EncryptionType messageBodyBodyEncryptionType,
                                      byte[] messageBodyEncryptionToken) {
        return switch (messageBodyBodyEncryptionType) {
            case AES -> CryptographyUtil.INSTANCE.aesDecrypt(messageBodyEncryptionToken,
                    messageBodyByteArrayBeforeDecrypt);
            case BLOWFISH -> CryptographyUtil.INSTANCE.blowfishDecrypt(messageBodyEncryptionToken,
                    messageBodyByteArrayBeforeDecrypt);
        };
    }

    private <T extends MessageBodyType> ByteBuf encodeMessageBody(MessageBody<T> messageBody,
                                                                  EncryptionType messageBodyBodyEncryptionType,
                                                                  byte[] messageBodyEncryptionToken) {
        var tempBuffer = Unpooled.buffer();
        var bodyType = messageBody.getBodyType().value();
        tempBuffer.writeByte(bodyType);
        var messageIdByteArray = messageBody.getId().getBytes(StandardCharsets.UTF_8);
        tempBuffer.writeInt(messageIdByteArray.length);
        tempBuffer.writeBytes(messageIdByteArray);
        var userTokenByteArray = messageBody.getUserToken().getBytes(StandardCharsets.UTF_8);
        tempBuffer.writeInt(userTokenByteArray.length);
        tempBuffer.writeBytes(userTokenByteArray);
        var targetAddressByteArray = messageBody.getTargetHost().getBytes(StandardCharsets.UTF_8);
        tempBuffer.writeInt(targetAddressByteArray.length);
        tempBuffer.writeBytes(targetAddressByteArray);
        tempBuffer.writeInt(messageBody.getTargetPort());
        var targetOriginalData = messageBody.getData();
        tempBuffer.writeInt(targetOriginalData.length);
        tempBuffer.writeBytes(targetOriginalData);
        return Unpooled.wrappedBuffer(encryptMessageBody(
                tempBuffer.array(),
                messageBodyBodyEncryptionType,
                messageBodyEncryptionToken));
    }

    private AgentMessageBody decodeAgentMessageBody(byte[] messageBytes,
                                                    EncryptionType messageBodyBodyEncryptionType,
                                                    byte[] messageBodyEncryptionToken) {
        var messageBodyBytes =
                decryptMessageBody(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken);
        var messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes);
        var bodyType = parseAgentMessageBodyType(messageBodyByteBuf.readByte());
        if (bodyType == null) {
            throw new PpaassException(
                    "Can not parse agent message body type from the message.");
        }
        var messageIdLength = messageBodyByteBuf.readInt();
        var messageId =
                messageBodyByteBuf.readCharSequence(messageIdLength, StandardCharsets.UTF_8).toString();
        var userTokenLength = messageBodyByteBuf.readInt();
        var userToken =
                messageBodyByteBuf.readCharSequence(userTokenLength, StandardCharsets.UTF_8).toString();
        var targetAddressLength = messageBodyByteBuf.readInt();
        var targetAddress = messageBodyByteBuf.readCharSequence(targetAddressLength,
                StandardCharsets.UTF_8).toString();
        var targetPort = messageBodyByteBuf.readInt();
        var originalDataLength = messageBodyByteBuf.readInt();
        var originalData = new byte[originalDataLength];
        messageBodyByteBuf.readBytes(originalData);
        return new AgentMessageBody(messageId, userToken, targetAddress, targetPort, bodyType, originalData);
    }

    private ProxyMessageBody decodeProxyMessageBody(byte[] messageBytes,
                                                    EncryptionType messageBodyBodyEncryptionType,
                                                    byte[] messageBodyEncryptionToken) {
        var messageBodyBytes =
                decryptMessageBody(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken);
        var messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes);
        var bodyType = parseProxyMessageBodyType(messageBodyByteBuf.readByte());
        if (bodyType == null) {
            throw new PpaassException(
                    "Can not parse proxy message body type from the message.");
        }
        var messageIdLength = messageBodyByteBuf.readInt();
        var messageId =
                messageBodyByteBuf.readCharSequence(messageIdLength, StandardCharsets.UTF_8).toString();
        var userTokenLength = messageBodyByteBuf.readInt();
        var userToken =
                messageBodyByteBuf.readCharSequence(userTokenLength, StandardCharsets.UTF_8).toString();
        var targetAddressLength = messageBodyByteBuf.readInt();
        var targetAddress = messageBodyByteBuf.readCharSequence(targetAddressLength,
                StandardCharsets.UTF_8).toString();
        var targetPort = messageBodyByteBuf.readInt();
        var originalDataLength = messageBodyByteBuf.readInt();
        var originalData = new byte[originalDataLength];
        messageBodyByteBuf.readBytes(originalData);
        return new ProxyMessageBody(messageId, userToken, targetAddress, targetPort, bodyType, originalData);
    }

    public String generateUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public byte[] generateUuidInBytes() {
        return this.generateUuid().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encode a message to byte buffer.
     *
     * @param message   The message to encode.
     * @param publicKey The public key base64
     * @param output    The output byte buffer
     */
    public <T extends MessageBodyType> void encodeMessage(Message<T> message,
                                                          byte[] publicKey,
                                                          ByteBuf output) {
        output.writeBytes(MAGIC_CODE);
        var originalMessageBodyEncryptionToken = message.getEncryptionToken();
        var encryptedMessageBodyEncryptionToken =
                CryptographyUtil.INSTANCE.rsaEncrypt(originalMessageBodyEncryptionToken,
                        publicKey);
        output.writeInt(encryptedMessageBodyEncryptionToken.length);
        output.writeBytes(encryptedMessageBodyEncryptionToken);
        output.writeByte(message.getEncryptionType().value());
        var bodyByteBuf = encodeMessageBody(message.getBody(),
                message.getEncryptionType(),
                originalMessageBodyEncryptionToken);
        output.writeBytes(bodyByteBuf);
    }

    /**
     * Decode agent message from input byte buffer.
     *
     * @param input           The input byte buffer.
     * @param proxyPrivateKey The proxy private key
     * @return The agent message
     */
    public AgentMessage decodeAgentMessage(ByteBuf input,
                                           byte[] proxyPrivateKey) {
        var magicCodeByteBuf = input.readBytes(MAGIC_CODE.length);
        if (magicCodeByteBuf.compareTo(Unpooled.wrappedBuffer(MAGIC_CODE)) != 0) {
            logger.error(
                    "Incoming agent message is not follow Ppaass protocol, incoming message is:\n{}\n"
                    , ByteBufUtil.prettyHexDump(input));
            throw new PpaassException("Incoming message is not follow Ppaass protocol.");
        }
        ReferenceCountUtil.release(magicCodeByteBuf);
        var encryptedMessageBodyEncryptionTokenLength = input.readInt();
        var encryptedMessageBodyEncryptionToken = new byte[encryptedMessageBodyEncryptionTokenLength];
        input.readBytes(encryptedMessageBodyEncryptionToken);
        var messageBodyEncryptionToken =
                CryptographyUtil.INSTANCE.rsaDecrypt(encryptedMessageBodyEncryptionToken,
                        proxyPrivateKey);
        var messageBodyEncryptionType = parseEncryptionType(input.readByte());
        if (messageBodyEncryptionType == null) {
            throw new PpaassException(
                    "Can not parse encryption type from the message.");
        }
        var messageBodyByteArray = new byte[input.readableBytes()];
        input.readBytes(messageBodyByteArray);
        return new AgentMessage(messageBodyEncryptionToken,
                messageBodyEncryptionType,
                decodeAgentMessageBody(
                        messageBodyByteArray,
                        messageBodyEncryptionType,
                        messageBodyEncryptionToken));
    }

    /**
     * Decode proxy message from input byte buffer.
     *
     * @param input           The input byte buffer.
     * @param agentPrivateKey The agent private key
     * @return The proxy message
     */
    public ProxyMessage decodeProxyMessage(ByteBuf input,
                                           byte[] agentPrivateKey) {
        var magicCodeByteBuf = input.readBytes(MAGIC_CODE.length);
        if (magicCodeByteBuf.compareTo(Unpooled.wrappedBuffer(MAGIC_CODE)) != 0) {
            logger.error(
                    "Incoming proxy message is not follow Ppaass protocol, incoming message is:\n${}\n",
                    ByteBufUtil.prettyHexDump(input)
            );
            throw new PpaassException("Incoming message is not follow Ppaass protocol.");
        }
        ReferenceCountUtil.release(magicCodeByteBuf);
        var encryptedMessageBodyEncryptionTokenLength = input.readInt();
        var encryptedMessageBodyEncryptionToken = new byte[encryptedMessageBodyEncryptionTokenLength];
        input.readBytes(encryptedMessageBodyEncryptionToken);
        var messageBodyEncryptionToken =
                CryptographyUtil.INSTANCE.rsaDecrypt(encryptedMessageBodyEncryptionToken,
                        agentPrivateKey);
        var messageBodyEncryptionType = parseEncryptionType(input.readByte());
        if (messageBodyEncryptionType == null) {
            throw new PpaassException(
                    "Can not parse encryption type from the message.");
        }
        var messageBodyByteArray = new byte[input.readableBytes()];
        input.readBytes(messageBodyByteArray);
        return new ProxyMessage(messageBodyEncryptionToken,
                messageBodyEncryptionType,
                decodeProxyMessageBody(
                        messageBodyByteArray,
                        messageBodyEncryptionType,
                        messageBodyEncryptionToken));
    }
}
