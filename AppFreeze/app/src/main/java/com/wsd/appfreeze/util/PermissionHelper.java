package com.wsd.appfreeze.util;

import android.content.Context;
import android.util.Log;

import com.wsd.appfreeze.adb.AdbShellHelper;

/**
 * 权限/连接状态检测工具类
 * 检测应用是否能通过 ADB 连接本机 adbd 执行 shell 命令。
 */
public class PermissionHelper {

    private static final String TAG = "PermissionHelper";

    /**
     * 检测 ADB 连接是否可用
     * 尝试连接 localhost:5555 并执行测试命令
     *
     * @param context 上下文
     * @return true 表示 ADB 连接可用，可以强制停止应用
     */
    public static boolean isAdbAvailable(Context context) {
        try {
            AdbShellHelper helper = new AdbShellHelper(context);
            boolean result = helper.testConnection();
            Log.i(TAG, "ADB 连接状态: " + (result ? "可用" : "不可用"));
            return result;
        } catch (Exception e) {
            Log.e(TAG, "ADB 连接检测异常: " + e.getMessage());
            return false;
        }
    }
}
