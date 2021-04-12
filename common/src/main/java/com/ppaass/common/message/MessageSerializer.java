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
        for (AgentMessageBodyType e : AgentMessageBodyType.values()) {
            if (e.value() == b) {
                return e;
            }
        }
        return null;
    }

    private ProxyMessageBodyType parseProxyMessageBodyType(byte b) {
        for (ProxyMessageBodyType e : ProxyMessageBodyType.values()) {
            if (e.value() == b) {
                return e;
            }
        }
        return null;
    }

    private EncryptionType parseEncryptionType(byte b) {
        for (EncryptionType e : EncryptionType.values()) {
            if (e.value() == b) {
                return e;
            }
        }
        return null;
    }

    private byte[] encryptMessageBody(byte[] messageBodyByteArrayBeforeEncrypt,
                                      EncryptionType messageBodyBodyEncryptionType,
                                      byte[] messageBodyEncryptionToken) {
        switch (messageBodyBodyEncryptionType) {
            case AES:
                return CryptographyUtil.INSTANCE.aesEncrypt(messageBodyEncryptionToken,
                        messageBodyByteArrayBeforeEncrypt);
            case BLOWFISH:
                return CryptographyUtil.INSTANCE.blowfishEncrypt(messageBodyEncryptionToken,
                        messageBodyByteArrayBeforeEncrypt);
            default:
                throw new PpaassException("Unsupported encryption type. ");
        }
    }

    private byte[] decryptMessageBody(byte[] messageBodyByteArrayBeforeDecrypt,
                                      EncryptionType messageBodyBodyEncryptionType,
                                      byte[] messageBodyEncryptionToken) {
        switch (messageBodyBodyEncryptionType) {
            case AES:
                return CryptographyUtil.INSTANCE.aesDecrypt(messageBodyEncryptionToken,
                        messageBodyByteArrayBeforeDecrypt);
            case BLOWFISH:
                return CryptographyUtil.INSTANCE.blowfishDecrypt(messageBodyEncryptionToken,
                        messageBodyByteArrayBeforeDecrypt);
            default:
                throw new PpaassException("Unsupported encryption type. ");
        }
    }

    private <T extends MessageBodyType> ByteBuf encodeMessageBody(MessageBody<T> messageBody,
                                                                  EncryptionType messageBodyBodyEncryptionType,
                                                                  byte[] messageBodyEncryptionToken) {
        ByteBuf tempBuffer = Unpooled.buffer();
        int bodyType = messageBody.getBodyType().value();
        tempBuffer.writeByte(bodyType);
        byte[] messageIdByteArray = messageBody.getId().getBytes(StandardCharsets.UTF_8);
        tempBuffer.writeInt(messageIdByteArray.length);
        tempBuffer.writeBytes(messageIdByteArray);
        byte[] userTokenByteArray = messageBody.getUserToken().getBytes(StandardCharsets.UTF_8);
        tempBuffer.writeInt(userTokenByteArray.length);
        tempBuffer.writeBytes(userTokenByteArray);
        byte[] targetAddressByteArray = messageBody.getTargetHost().getBytes(StandardCharsets.UTF_8);
        tempBuffer.writeInt(targetAddressByteArray.length);
        tempBuffer.writeBytes(targetAddressByteArray);
        tempBuffer.writeInt(messageBody.getTargetPort());
        byte[] targetOriginalData = messageBody.getData();
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
        byte[] messageBodyBytes =
                decryptMessageBody(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken);
        ByteBuf messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes);
        AgentMessageBodyType bodyType = parseAgentMessageBodyType(messageBodyByteBuf.readByte());
        if (bodyType == null) {
            throw new PpaassException(
                    "Can not parse agent message body type from the message.");
        }
        int messageIdLength = messageBodyByteBuf.readInt();
        String messageId =
                messageBodyByteBuf.readCharSequence(messageIdLength, StandardCharsets.UTF_8).toString();
        int userTokenLength = messageBodyByteBuf.readInt();
        String userToken =
                messageBodyByteBuf.readCharSequence(userTokenLength, StandardCharsets.UTF_8).toString();
        int targetAddressLength = messageBodyByteBuf.readInt();
        String targetAddress = messageBodyByteBuf.readCharSequence(targetAddressLength,
                StandardCharsets.UTF_8).toString();
        int targetPort = messageBodyByteBuf.readInt();
        int originalDataLength = messageBodyByteBuf.readInt();
        byte[] originalData = new byte[originalDataLength];
        messageBodyByteBuf.readBytes(originalData);
        return new AgentMessageBody(messageId, userToken, targetAddress, targetPort, bodyType, originalData);
    }

    private ProxyMessageBody decodeProxyMessageBody(byte[] messageBytes,
                                                    EncryptionType messageBodyBodyEncryptionType,
                                                    byte[] messageBodyEncryptionToken) {
        byte[] messageBodyBytes =
                decryptMessageBody(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken);
        ByteBuf messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes);
        ProxyMessageBodyType bodyType = parseProxyMessageBodyType(messageBodyByteBuf.readByte());
        if (bodyType == null) {
            throw new PpaassException(
                    "Can not parse proxy message body type from the message.");
        }
        int messageIdLength = messageBodyByteBuf.readInt();
        String messageId =
                messageBodyByteBuf.readCharSequence(messageIdLength, StandardCharsets.UTF_8).toString();
        int userTokenLength = messageBodyByteBuf.readInt();
        String userToken =
                messageBodyByteBuf.readCharSequence(userTokenLength, StandardCharsets.UTF_8).toString();
        int targetAddressLength = messageBodyByteBuf.readInt();
        String targetAddress = messageBodyByteBuf.readCharSequence(targetAddressLength,
                StandardCharsets.UTF_8).toString();
        int targetPort = messageBodyByteBuf.readInt();
        int originalDataLength = messageBodyByteBuf.readInt();
        byte[] originalData = new byte[originalDataLength];
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
        byte[] originalMessageBodyEncryptionToken = message.getEncryptionToken();
        byte[] encryptedMessageBodyEncryptionToken =
                CryptographyUtil.INSTANCE.rsaEncrypt(originalMessageBodyEncryptionToken,
                        publicKey);
        output.writeInt(encryptedMessageBodyEncryptionToken.length);
        output.writeBytes(encryptedMessageBodyEncryptionToken);
        output.writeByte(message.getEncryptionType().value());
        ByteBuf bodyByteBuf = encodeMessageBody(message.getBody(),
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
        ByteBuf magicCodeByteBuf = input.readBytes(MAGIC_CODE.length);
        if (magicCodeByteBuf.compareTo(Unpooled.wrappedBuffer(MAGIC_CODE)) != 0) {
            logger.error(
                    "Incoming agent message is not follow Ppaass protocol, incoming message is:\n{}\n"
                    , ByteBufUtil.prettyHexDump(input));
            throw new PpaassException("Incoming message is not follow Ppaass protocol.");
        }
        ReferenceCountUtil.release(magicCodeByteBuf);
        int encryptedMessageBodyEncryptionTokenLength = input.readInt();
        byte[] encryptedMessageBodyEncryptionToken = new byte[encryptedMessageBodyEncryptionTokenLength];
        input.readBytes(encryptedMessageBodyEncryptionToken);
        byte[] messageBodyEncryptionToken =
                CryptographyUtil.INSTANCE.rsaDecrypt(encryptedMessageBodyEncryptionToken,
                        proxyPrivateKey);
        EncryptionType messageBodyEncryptionType = parseEncryptionType(input.readByte());
        if (messageBodyEncryptionType == null) {
            throw new PpaassException(
                    "Can not parse encryption type from the message.");
        }
        byte[] messageBodyByteArray = new byte[input.readableBytes()];
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
        ByteBuf magicCodeByteBuf = input.readBytes(MAGIC_CODE.length);
        if (magicCodeByteBuf.compareTo(Unpooled.wrappedBuffer(MAGIC_CODE)) != 0) {
            logger.error(
                    "Incoming proxy message is not follow Ppaass protocol, incoming message is:\n${}\n",
                    ByteBufUtil.prettyHexDump(input)
            );
            throw new PpaassException("Incoming message is not follow Ppaass protocol.");
        }
        ReferenceCountUtil.release(magicCodeByteBuf);
        int encryptedMessageBodyEncryptionTokenLength = input.readInt();
        byte[] encryptedMessageBodyEncryptionToken = new byte[encryptedMessageBodyEncryptionTokenLength];
        input.readBytes(encryptedMessageBodyEncryptionToken);
        byte[] messageBodyEncryptionToken =
                CryptographyUtil.INSTANCE.rsaDecrypt(encryptedMessageBodyEncryptionToken,
                        agentPrivateKey);
        EncryptionType messageBodyEncryptionType = parseEncryptionType(input.readByte());
        if (messageBodyEncryptionType == null) {
            throw new PpaassException(
                    "Can not parse encryption type from the message.");
        }
        byte[] messageBodyByteArray = new byte[input.readableBytes()];
        input.readBytes(messageBodyByteArray);
        return new ProxyMessage(messageBodyEncryptionToken,
                messageBodyEncryptionType,
                decodeProxyMessageBody(
                        messageBodyByteArray,
                        messageBodyEncryptionType,
                        messageBodyEncryptionToken));
    }
}
