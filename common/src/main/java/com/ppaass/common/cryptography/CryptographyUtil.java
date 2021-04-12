package com.ppaass.common.cryptography;

import com.ppaass.common.exception.PpaassException;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class CryptographyUtil {
    private static final Logger logger = LoggerFactory.getLogger(CryptographyUtil.class);
    private static final String ALGORITHM_RSA = "RSA";
    private static final String RSA_CHIPHER = "RSA/ECB/PKCS1Padding";
    private static final String ALGORITHM_AES = "AES";
    private static final String ALGORITHM_BLOWFISH = "Blowfish";
    private static final String AES_CIPHER = "AES/ECB/PKCS5Padding";
    private static final String BLOWFISH_CIPHER = "Blowfish/ECB/PKCS5Padding";
    public static final CryptographyUtil INSTANCE = new CryptographyUtil();

    private CryptographyUtil() {
    }

    /**
     * Do AES encryption with encryption token.
     *
     * @param encryptionToken Encryption token.
     * @param data            The data to do encryption.
     * @return The encrypt result
     */
    public byte[] aesEncrypt(byte[] encryptionToken, byte[] data) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionToken, ALGORITHM_AES);
            Cipher cipher = Cipher.getInstance(AES_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            logger.error(
                    "Fail to encrypt data with encryption token in AES because of exception. Encryption token: \n{}\n",
                    ByteBufUtil
                            .prettyHexDump(Unpooled.wrappedBuffer(encryptionToken)), e);
            throw new PpaassException(
                    "Fail to encrypt data with encryption token in AES because of exception.",
                    e);
        }
    }

    /**
     * Decrypt AES data with encryption token.
     *
     * @param encryptionToken Encryption token.
     * @param aesData         The data encrypted.
     * @return The original data
     */
    public byte[] aesDecrypt(byte[] encryptionToken, byte[] aesData) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionToken, ALGORITHM_AES);
            Cipher cipher = Cipher.getInstance(AES_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(aesData);
        } catch (Exception e) {
            logger.error(
                    "Fail to decrypt data with encryption token in AES because of exception. Encryption token: \n{}\n",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(encryptionToken)));
            throw new PpaassException(
                    "Fail to decrypt data with encryption token in AES because of exception.",
                    e);
        }
    }

    /**
     * Do Blowfish encryption with encryption token.
     *
     * @param encryptionToken Encryption token.
     * @param data            The data to do encryption.
     * @return The encrypt result
     */
    public byte[] blowfishEncrypt(byte[] encryptionToken, byte[] data) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionToken, ALGORITHM_BLOWFISH);
            Cipher cipher = Cipher.getInstance(BLOWFISH_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            logger.error(
                    "Fail to encrypt data with encryption token in Blowfish because of exception. Encryption token: \n{}\n",
                    ByteBufUtil
                            .prettyHexDump(Unpooled.wrappedBuffer(encryptionToken)), e);
            throw new PpaassException(
                    "Fail to encrypt data with encryption token in Blowfish because of exception.",
                    e);
        }
    }

    /**
     * Decrypt Blowfish data with encryption token.
     *
     * @param encryptionToken Encryption token.
     * @param blowfishData    The data encrypted.
     * @return The original data
     */
    public byte[] blowfishDecrypt(byte[] encryptionToken, byte[] blowfishData) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionToken, ALGORITHM_BLOWFISH);
            Cipher cipher = Cipher.getInstance(BLOWFISH_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(blowfishData);
        } catch (Exception e) {
            logger.error(
                    "Fail to decrypt data with encryption token in Blowfish because of exception. Encryption token: \n{}\n",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(encryptionToken)));
            throw new PpaassException(
                    "Fail to decrypt data with encryption token in Blowfish because of exception.",
                    e);
        }
    }

    /**
     * Do RSA encryption with public key.
     *
     * @param target         Target data to do encrypt.
     * @param publicKeyBytes The public key.
     * @return The encrypt result
     */
    public byte[] rsaEncrypt(byte[] target, byte[] publicKeyBytes) {
        try {
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            Cipher cipher = Cipher.getInstance(RSA_CHIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            cipher.update(target);
            return cipher.doFinal();
        } catch (Exception e) {
            logger.error(
                    "Fail to encrypt data with rsa public key because of exception. Target data: \n{}\nRSA public key: \n{}\n"
                    , ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(target)),
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(publicKeyBytes)), e);
            throw new PpaassException("Fail to encrypt data with rsa public key because of exception.", e);
        }
    }

    /**
     * Do RSA decryption with private key.
     *
     * @param target          Target data to do decrypt.
     * @param privateKeyBytes The private key.
     * @return The decrypt result
     */
    public byte[] rsaDecrypt(byte[] target, byte[] privateKeyBytes) {
        try {
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
            Cipher cipher = Cipher.getInstance(RSA_CHIPHER);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            cipher.update(target);
            return cipher.doFinal();
        } catch (Exception e) {
            logger.error(
                    "Fail to decrypt data with rsa private key because of exception. Target data:\n{}\nRSA private key:\n{}\n"
                    , ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(target)),
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(privateKeyBytes)), e);
            throw new PpaassException("Fail to decrypt data with rsa private key because of exception.", e);
        }
    }

    private static class RsaKeyPair {
        private final byte[] publicKey;
        private final byte[] privateKey;

        public RsaKeyPair(byte[] publicKey, byte[] privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public byte[] getPrivateKey() {
            return privateKey;
        }
    }

    private void writeBytesToFile(Path filePath, byte[] bytes) throws IOException {
        File targetFile = filePath.toFile();
        if (targetFile.exists()) {
            if (!targetFile.delete()) {
                logger.error("Fail to delete existing file: {}", filePath);
                throw new PpaassException("Fail to delete existing file.");
            }
        }
        FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
        fileOutputStream.write(bytes);
        fileOutputStream.close();
    }

    private RsaKeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(ALGORITHM_RSA);
        keyPairGen.initialize(1024);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        byte[] publicKey = keyPair.getPublic().getEncoded();
        logger.info(
                "RSA key pair public key:\n${}", ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(publicKey))
        );
        byte[] privateKey = keyPair.getPrivate().getEncoded();
        logger.info(
                "RSA key pair private key:\n{}", ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(privateKey)));
        return new RsaKeyPair(publicKey, privateKey);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\nGenerate agent RSA key pair:\n");
        CryptographyUtil.RsaKeyPair agentKeyPair = CryptographyUtil.INSTANCE.generateRsaKeyPair();
        CryptographyUtil.INSTANCE.writeBytesToFile(Path.of("D://", "agentPublicKey"), agentKeyPair.getPublicKey());
        CryptographyUtil.INSTANCE.writeBytesToFile(Path.of("D://", "agentPrivateKey"), agentKeyPair.getPrivateKey());
        System.out.println("\nGenerate proxy RSA key pair:\n");
        CryptographyUtil.RsaKeyPair proxyKeyPair = CryptographyUtil.INSTANCE.generateRsaKeyPair();
        CryptographyUtil.INSTANCE.writeBytesToFile(Path.of("D://", "proxyPublicKey"), proxyKeyPair.getPublicKey());
        CryptographyUtil.INSTANCE.writeBytesToFile(Path.of("D://", "proxyPrivateKey"), proxyKeyPair.getPrivateKey());
    }
}
