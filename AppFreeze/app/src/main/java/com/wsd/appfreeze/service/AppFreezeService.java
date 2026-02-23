package com.wsd.appfreeze.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.wsd.appfreeze.R;
import com.wsd.appfreeze.receiver.ScreenOffReceiver;

/**
 * 前台服务 - 持续监听电视待机事件
 *
 * 动态注册 ScreenOffReceiver 监听 ACTION_SCREEN_OFF 广播。
 * 当电视待机时，通过内嵌 ADB 客户端执行 am force-stop 命令关闭应用。
 */
public class AppFreezeService extends Service {

    private static final String TAG = "AppFreezeService";
    private static final String CHANNEL_ID = "app_freeze_channel";
    private static final int NOTIFICATION_ID = 1001;

    private ScreenOffReceiver screenOffReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "AppFreezeService 已创建");

        screenOffReceiver = new ScreenOffReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenOffReceiver, filter);
        Log.i(TAG, "已注册屏幕关闭广播监听");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        Log.i(TAG, "前台服务已启动，正在监听电视待机事件...");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver);
            screenOffReceiver = null;
        }
        Log.i(TAG, "AppFreezeService 已销毁");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "应用冻结服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("监听电视待机事件，自动关闭后台应用");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("AppFreeze 运行中")
                .setContentText("正在监听电视待机事件，自动关闭后台应用")
                .setSmallIcon(R.drawable.app_icon_your_company)
                .setOngoing(true)
                .build();
    }
}
