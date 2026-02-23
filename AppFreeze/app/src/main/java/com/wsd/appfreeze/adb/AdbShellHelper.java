package com.wsd.appfreeze.adb;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * ADB Shell 命令执行助手
 *
 * 核心原理：
 * 索尼电视开启无线调试后，adbd 守护进程监听在 localhost:5555。
 * 本类通过 TCP socket 连接本机 adbd，完成 RSA 认证后，
 * 以 shell 身份执行 am force-stop 命令，彻底终止目标应用。
 *
 * ADB shell 拥有 FORCE_STOP_PACKAGES 权限，这是 Android 系统设计如此，
 * 不需要 root，不需要系统签名，不需要 Device Owner。
 *
 * 首次连接时需要用户在电视上确认 ADB 授权弹窗（勾选"始终允许"后不再弹出）。
 */
public class AdbShellHelper {

    private static final String TAG = "AdbShellHelper";
    private static final String ADB_HOST = "127.0.0.1";
    private static final int ADB_PORT = 5555;
    private static final int CONNECT_TIMEOUT = 5000;

    private final Context context;
    private AdbCrypto crypto;

    public AdbShellHelper(Context context) {
        this.context = context;
        initCrypto();
    }

    /**
     * 初始化 RSA 密钥对
     * 首次运行时生成新密钥对并保存到应用私有目录，
     * 后续运行直接加载已有密钥。
     */
    private void initCrypto() {
        File privateKeyFile = new File(context.getFilesDir(), "adb_key");
        File publicKeyFile = new File(context.getFilesDir(), "adb_key.pub");

        AdbBase64 base64 = data -> Base64.encodeToString(data, Base64.NO_WRAP);

        try {
            if (privateKeyFile.exists() && publicKeyFile.exists()) {
                // 加载已有密钥对
                crypto = AdbCrypto.loadAdbKeyPair(base64, privateKeyFile, publicKeyFile);
                Log.i(TAG, "已加载 ADB RSA 密钥对");
            } else {
                // 生成新密钥对
                crypto = AdbCrypto.generateAdbKeyPair(base64);
                crypto.saveAdbKeyPair(privateKeyFile, publicKeyFile);
                Log.i(TAG, "已生成并保存新的 ADB RSA 密钥对");
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化 ADB 密钥失败: " + e.getMessage());
            try {
                crypto = AdbCrypto.generateAdbKeyPair(base64);
            } catch (NoSuchAlgorithmException ex) {
                Log.e(TAG, "生成密钥对失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 通过 ADB shell 批量强制停止应用
     *
     * @param packageNames 待停止的应用包名集合
     * @return 成功停止的应用数量
     */
    public int forceStopApps(Set<String> packageNames) {
        if (crypto == null) {
            Log.e(TAG, "ADB 密钥未初始化");
            return 0;
        }

        AdbConnection connection = null;
        int count = 0;

        try {
            // 连接本机 adbd
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(ADB_HOST, ADB_PORT), CONNECT_TIMEOUT);
            connection = AdbConnection.create(socket, crypto);
            connection.connect();
            Log.i(TAG, "已连接到本机 adbd (localhost:" + ADB_PORT + ")");

            // 逐个执行 am force-stop
            for (String packageName : packageNames) {
                try {
                    AdbStream stream = connection.open("shell:am force-stop " + packageName);

                    // 读取命令输出（等待命令执行完成）
                    try {
                        byte[] response = stream.read();
                        if (response != null) {
                            String output = new String(response).trim();
                            if (!output.isEmpty()) {
                                Log.d(TAG, "命令输出 [" + packageName + "]: " + output);
                            }
                        }
                    } catch (Exception e) {
                        // 流关闭表示命令执行完成，这是正常的
                    }

                    stream.close();
                    count++;
                    Log.i(TAG, "已强制停止: " + packageName);
                } catch (Exception e) {
                    Log.w(TAG, "强制停止失败: " + packageName + " - " + e.getMessage());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "ADB 连接失败: " + e.getMessage()
                    + "（请确保电视已开启无线调试，且已授权本应用的 ADB 连接）");
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    Log.w(TAG, "关闭 ADB 连接异常: " + e.getMessage());
                }
            }
        }

        return count;
    }

    /**
     * 测试 ADB 连接是否可用
     *
     * @return true 表示可以成功连接并执行命令
     */
    public boolean testConnection() {
        if (crypto == null) return false;

        AdbConnection connection = null;
        try {
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(ADB_HOST, ADB_PORT), CONNECT_TIMEOUT);
            connection = AdbConnection.create(socket, crypto);
            connection.connect();

            // 执行一个简单的测试命令
            AdbStream stream = connection.open("shell:echo adb_ok");
            try {
                byte[] response = stream.read();
                if (response != null) {
                    String output = new String(response).trim();
                    Log.i(TAG, "ADB 连接测试成功: " + output);
                    return output.contains("adb_ok");
                }
            } catch (Exception e) {
                // 流关闭也算成功
            }
            stream.close();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "ADB 连接测试失败: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                try { connection.close(); } catch (Exception e) { }
            }
        }
    }
}
