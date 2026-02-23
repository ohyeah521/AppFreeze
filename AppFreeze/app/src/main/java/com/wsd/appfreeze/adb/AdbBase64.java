package com.wsd.appfreeze.adb;

/**
 * ADB Base64 编码接口
 * ADB 协议认证过程中需要对 RSA 公钥进行 Base64 编码
 * 基于 cgutman/AdbLib 开源库（Apache 2.0 许可证）
 */
public interface AdbBase64 {
    /**
     * 将指定数据编码为 Base64 字符串
     *
     * @param data 待编码数据
     * @return Base64 编码后的字符串
     */
    String encodeToString(byte[] data);
}
