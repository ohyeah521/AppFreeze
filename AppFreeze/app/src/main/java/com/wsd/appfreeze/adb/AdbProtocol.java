package com.wsd.appfreeze.adb;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ADB 协议实现
 * 处理 ADB 消息的生成、解析和校验
 * 基于 cgutman/AdbLib 开源库（Apache 2.0 许可证）
 */
public class AdbProtocol {

    /** ADB 消息头长度（24字节） */
    public static final int ADB_HEADER_LENGTH = 24;

    /** 连接命令 */
    public static final int CMD_CNXN = 0x4e584e43;
    /** 认证命令 */
    public static final int CMD_AUTH = 0x48545541;
    /** 打开流命令 */
    public static final int CMD_OPEN = 0x4e45504f;
    /** 确认命令 */
    public static final int CMD_OKAY = 0x59414b4f;
    /** 关闭流命令 */
    public static final int CMD_CLSE = 0x45534c43;
    /** 写入数据命令 */
    public static final int CMD_WRTE = 0x45545257;

    /** ADB 协议版本 */
    public static final int CONNECT_VERSION = 0x01000000;
    /** 最大数据负载 */
    public static final int CONNECT_MAXDATA = 4096;

    /** 认证类型：SHA1 令牌 */
    public static final int AUTH_TYPE_TOKEN = 1;
    /** 认证类型：签名 */
    public static final int AUTH_TYPE_SIGNATURE = 2;
    /** 认证类型：RSA 公钥 */
    public static final int AUTH_TYPE_RSA_PUBLIC = 3;

    /** 连接消息负载 */
    public static byte[] CONNECT_PAYLOAD;
    static {
        try {
            CONNECT_PAYLOAD = "host::\0".getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) { }
    }

    /** 计算负载校验和 */
    private static int getPayloadChecksum(byte[] payload) {
        int checksum = 0;
        for (byte b : payload) {
            checksum += (b >= 0) ? b : (b + 256);
        }
        return checksum;
    }

    /** 验证 ADB 消息的有效性 */
    public static boolean validateMessage(AdbMessage msg) {
        if (msg.command != (msg.magic ^ 0xFFFFFFFF))
            return false;
        if (msg.payloadLength != 0) {
            if (getPayloadChecksum(msg.payload) != msg.checksum)
                return false;
        }
        return true;
    }

    /** 生成 ADB 消息 */
    public static byte[] generateMessage(int cmd, int arg0, int arg1, byte[] payload) {
        ByteBuffer message;
        if (payload != null) {
            message = ByteBuffer.allocate(ADB_HEADER_LENGTH + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        } else {
            message = ByteBuffer.allocate(ADB_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        }

        message.putInt(cmd);
        message.putInt(arg0);
        message.putInt(arg1);

        if (payload != null) {
            message.putInt(payload.length);
            message.putInt(getPayloadChecksum(payload));
        } else {
            message.putInt(0);
            message.putInt(0);
        }

        message.putInt(cmd ^ 0xFFFFFFFF);

        if (payload != null) {
            message.put(payload);
        }

        return message.array();
    }

    public static byte[] generateConnect() {
        return generateMessage(CMD_CNXN, CONNECT_VERSION, CONNECT_MAXDATA, CONNECT_PAYLOAD);
    }

    public static byte[] generateAuth(int type, byte[] data) {
        return generateMessage(CMD_AUTH, type, 0, data);
    }

    public static byte[] generateOpen(int localId, String dest) throws UnsupportedEncodingException {
        ByteBuffer bbuf = ByteBuffer.allocate(dest.length() + 1);
        bbuf.put(dest.getBytes("UTF-8"));
        bbuf.put((byte) 0);
        return generateMessage(CMD_OPEN, localId, 0, bbuf.array());
    }

    public static byte[] generateWrite(int localId, int remoteId, byte[] data) {
        return generateMessage(CMD_WRTE, localId, remoteId, data);
    }

    public static byte[] generateClose(int localId, int remoteId) {
        return generateMessage(CMD_CLSE, localId, remoteId, null);
    }

    public static byte[] generateReady(int localId, int remoteId) {
        return generateMessage(CMD_OKAY, localId, remoteId, null);
    }

    /** ADB 消息结构 */
    public static final class AdbMessage {
        public int command;
        public int arg0;
        public int arg1;
        public int payloadLength;
        public int checksum;
        public int magic;
        public byte[] payload;

        /** 从输入流解析 ADB 消息 */
        public static AdbMessage parseAdbMessage(InputStream in) throws IOException {
            AdbMessage msg = new AdbMessage();
            ByteBuffer packet = ByteBuffer.allocate(ADB_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);

            int dataRead = 0;
            do {
                int bytesRead = in.read(packet.array(), dataRead, ADB_HEADER_LENGTH - dataRead);
                if (bytesRead < 0) throw new IOException("Stream closed");
                dataRead += bytesRead;
            } while (dataRead < ADB_HEADER_LENGTH);

            msg.command = packet.getInt();
            msg.arg0 = packet.getInt();
            msg.arg1 = packet.getInt();
            msg.payloadLength = packet.getInt();
            msg.checksum = packet.getInt();
            msg.magic = packet.getInt();

            if (msg.payloadLength != 0) {
                msg.payload = new byte[msg.payloadLength];
                dataRead = 0;
                do {
                    int bytesRead = in.read(msg.payload, dataRead, msg.payloadLength - dataRead);
                    if (bytesRead < 0) throw new IOException("Stream closed");
                    dataRead += bytesRead;
                } while (dataRead < msg.payloadLength);
            }

            return msg;
        }
    }
}
