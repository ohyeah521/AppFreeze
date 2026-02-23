package com.wsd.appfreeze.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.wsd.appfreeze.service.AppFreezeService;

/**
 * 开机自启动广播接收器
 * 监听系统启动完成事件，自动启动 AppFreezeService 前台服务。
 * 确保电视每次开机后，应用冻结功能自动生效，无需用户手动打开应用。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "系统启动完成，正在启动 AppFreezeService...");
            startAppFreezeService(context);
        }
    }

    /**
     * 启动前台服务
     * Android 8.0+ 需要使用 startForegroundService 启动前台服务
     */
    private void startAppFreezeService(Context context) {
        Intent serviceIntent = new Intent(context, AppFreezeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        Log.i(TAG, "AppFreezeService 启动指令已发送");
    }
}
