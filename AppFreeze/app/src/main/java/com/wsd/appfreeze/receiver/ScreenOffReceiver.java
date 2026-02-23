package com.wsd.appfreeze.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Set;

import com.wsd.appfreeze.util.AppKiller;
import com.wsd.appfreeze.util.FreezeConfig;

/**
 * 屏幕关闭广播接收器（电视待机）
 *
 * 当索尼电视遥控器按下关机键时，系统进入待机模式，发送 ACTION_SCREEN_OFF 广播。
 * 本接收器通过内嵌 ADB 客户端执行 am force-stop 命令，彻底终止用户配置的应用。
 */
public class ScreenOffReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenOffReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            Log.i(TAG, "检测到屏幕关闭（电视待机），开始强制停止用户配置的应用...");

            FreezeConfig config = new FreezeConfig(context);
            Set<String> killList = config.getKillList();

            if (killList.isEmpty()) {
                Log.i(TAG, "待关闭应用列表为空，无需处理");
                return;
            }

            // 在新线程中执行，避免阻塞广播接收器（ADB 连接需要网络IO）
            new Thread(() -> {
                AppKiller killer = new AppKiller(context);
                int count = killer.killApps(killList);
                Log.i(TAG, "清理完成，共强制停止 " + count + " / " + killList.size() + " 个应用");
            }).start();
        }
    }
}
