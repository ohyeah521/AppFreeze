package com.wsd.appfreeze.util;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import com.wsd.appfreeze.adb.AdbShellHelper;

import java.util.Set;

/**
 * 应用关闭工具类（最终方案）
 *
 * 通过内嵌 ADB 客户端连接本机 adbd 守护进程（localhost:5555），
 * 以 shell 身份执行 am force-stop 命令，彻底终止目标应用。
 *
 * ADB shell 拥有 FORCE_STOP_PACKAGES 权限，这是 Android 系统设计如此。
 * 不需要 root，不需要系统签名，不需要 Device Owner。
 * 只需要电视开启无线调试，并且用户首次使用时确认 ADB 授权弹窗。
 *
 * 降级方案：如果 ADB 连接失败，使用 killBackgroundProcesses（效果有限）。
 */
public class AppKiller {

    private static final String TAG = "AppKiller";

    private final Context context;
    private final AdbShellHelper adbHelper;
    private final ActivityManager am;

    public AppKiller(Context context) {
        this.context = context;
        this.adbHelper = new AdbShellHelper(context);
        this.am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    /**
     * 批量强制停止应用
     *
     * @param packageNames 待关闭的应用包名集合
     * @return 成功处理的数量
     */
    public int killApps(Set<String> packageNames) {
        if (packageNames.isEmpty()) {
            Log.i(TAG, "待关闭应用列表为空");
            return 0;
        }

        Log.i(TAG, "开始关闭 " + packageNames.size() + " 个应用...");

        // 优先使用 ADB shell 方案（am force-stop）
        int count = adbHelper.forceStopApps(packageNames);

        if (count > 0) {
            Log.i(TAG, "ADB shell 方案成功，共强制停止 " + count + " / " + packageNames.size() + " 个应用");
            return count;
        }

        // ADB 连接失败，降级使用 killBackgroundProcesses
        Log.w(TAG, "ADB shell 方案失败，降级使用 killBackgroundProcesses");
        count = 0;
        for (String packageName : packageNames) {
            try {
                if (am != null) {
                    am.killBackgroundProcesses(packageName);
                    count++;
                    Log.i(TAG, "[降级] killBackgroundProcesses: " + packageName);
                }
            } catch (Exception e) {
                Log.w(TAG, "[降级] 失败: " + packageName + " - " + e.getMessage());
            }
        }

        return count;
    }

    /**
     * 测试 ADB 连接是否可用
     */
    public boolean testAdbConnection() {
        return adbHelper.testConnection();
    }
}
