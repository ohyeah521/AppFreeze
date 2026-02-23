package com.wsd.appfreeze.adb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * ADB RSA 加密工具类
 * 处理 ADB 协议所需的 RSA 密钥对生成、存储和签名操作
 * 基于 cgutman/AdbLib 开源库（Apache 2.0 许可证）
 */
public class AdbCrypto {

    private KeyPair keyPair;
    private AdbBase64 base64;

    public static final int KEY_LENGTH_BITS = 2048;
    public static final int KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8;
    public static final int KEY_LENGTH_WORDS = KEY_LENGTH_BYTES / 4;

    /** RSA 签名填充 */
    private static final int[] SIGNATURE_PADDING_AS_INT = new int[]{
            0x00, 0x01, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00,
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00,
            0x04, 0x14
    };

    public static byte[] SIGNATURE_PADDING;
    static {
        SIGNATURE_PADDING = new byte[SIGNATURE_PADDING_AS_INT.length];
        for (int i = 0; i < SIGNATURE_PADDING.length; i++)
            SIGNATURE_PADDING[i] = (byte) SIGNATURE_PADDING_AS_INT[i];
    }

    /** 将 RSA 公钥转换为 ADB 格式 */
    private static byte[] convertRsaPublicKeyToAdbFormat(RSAPublicKey pubkey) {
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger n = pubkey.getModulus();
        BigInteger r = BigInteger.ZERO.setBit(KEY_LENGTH_WORDS * 32);
        BigInteger rr = r.modPow(BigInteger.valueOf(2), n);
        BigInteger rem = n.remainder(r32);
        BigInteger n0inv = rem.modInverse(r32);

        int[] myN = new int[KEY_LENGTH_WORDS];
        int[] myRr = new int[KEY_LENGTH_WORDS];
        BigInteger[] res;
        for (int i = 0; i < KEY_LENGTH_WORDS; i++) {
            res = rr.divideAndRemainder(r32);
            rr = res[0];
            myRr[i] = res[1].intValue();
            res = n.divideAndRemainder(r32);
            n = res[0];
            myN[i] = res[1].intValue();
        }

        ByteBuffer bbuf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        bbuf.putInt(KEY_LENGTH_WORDS);
        bbuf.putInt(n0inv.negate().intValue());
        for (int i : myN) bbuf.putInt(i);
        for (int i : myRr) bbuf.putInt(i);
        bbuf.putInt(pubkey.getPublicExponent().intValue());
        return bbuf.array();
    }

    /** 从文件加载已有的密钥对 */
    public static AdbCrypto loadAdbKeyPair(AdbBase64 base64, File privateKey, File publicKey)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        AdbCrypto crypto = new AdbCrypto();

        byte[] privKeyBytes = new byte[(int) privateKey.length()];
        byte[] pubKeyBytes = new byte[(int) publicKey.length()];

        try (FileInputStream privIn = new FileInputStream(privateKey);
             FileInputStream pubIn = new FileInputStream(publicKey)) {
            privIn.read(privKeyBytes);
            pubIn.read(pubKeyBytes);
        }

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        crypto.keyPair = new KeyPair(
                keyFactory.generatePublic(new X509EncodedKeySpec(pubKeyBytes)),
                keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privKeyBytes)));
        crypto.base64 = base64;
        return crypto;
    }

    /** 生成新的 RSA 密钥对 */
    public static AdbCrypto generateAdbKeyPair(AdbBase64 base64) throws NoSuchAlgorithmException {
        AdbCrypto crypto = new AdbCrypto();
        KeyPairGenerator rsaKeyPg = KeyPairGenerator.getInstance("RSA");
        rsaKeyPg.initialize(KEY_LENGTH_BITS);
        crypto.keyPair = rsaKeyPg.genKeyPair();
        crypto.base64 = base64;
        return crypto;
    }

    /** 使用私钥签名 ADB 认证令牌 */
    public byte[] signAdbTokenPayload(byte[] payload) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, keyPair.getPrivate());
        c.update(SIGNATURE_PADDING);
        return c.doFinal(payload);
    }

    /** 获取 ADB 格式的 RSA 公钥 */
    public byte[] getAdbPublicKeyPayload() throws IOException {
        byte[] convertedKey = convertRsaPublicKeyToAdbFormat((RSAPublicKey) keyPair.getPublic());
        StringBuilder keyString = new StringBuilder(720);
        keyString.append(base64.encodeToString(convertedKey));
        keyString.append(" appfreeze@tv");
        keyString.append('\0');
        return keyString.toString().getBytes("UTF-8");
    }

    /** 保存密钥对到文件 */
    public void saveAdbKeyPair(File privateKey, File publicKey) throws IOException {
        try (FileOutputStream privOut = new FileOutputStream(privateKey);
             FileOutputStream pubOut = new FileOutputStream(publicKey)) {
            privOut.write(keyPair.getPrivate().getEncoded());
            pubOut.write(keyPair.getPublic().getEncoded());
        }
    }
}
