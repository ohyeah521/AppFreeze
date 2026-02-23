package com.wsd.appfreeze.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 冻结配置管理工具类
 * 使用 SharedPreferences 持久化存储用户选择的待关闭应用包名列表。
 * 每次电视待机时，ScreenOffReceiver 读取此配置，只关闭用户明确选择的应用。
 */
public class FreezeConfig {

    private static final String TAG = "FreezeConfig";
    private static final String PREFS_NAME = "app_freeze_config";
    private static final String KEY_KILL_LIST = "kill_package_list";

    /** 预置黑名单：无论用户是否勾选，待机时都会强制停止这些应用 */
    public static final Set<String> PRESET_BLACKLIST = new HashSet<>(Arrays.asList(
            "com.dangbei.TVHomeLauncher",
            "com.sony.dangbeimarket"
    ));

    private final SharedPreferences prefs;

    public FreezeConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 保存用户选择的待关闭应用包名列表
     *
     * @param packageNames 包名集合
     */
    public void saveKillList(Set<String> packageNames) {
        prefs.edit().putStringSet(KEY_KILL_LIST, packageNames).apply();
        Log.i(TAG, "已保存待关闭应用列表，共 " + packageNames.size() + " 个应用");
    }

    /**
     * 获取完整的待关闭应用列表（用户选择 + 预置黑名单）
     *
     * @return 包名集合
     */
    public Set<String> getKillList() {
        Set<String> list = new HashSet<>(prefs.getStringSet(KEY_KILL_LIST, new HashSet<>()));
        // 合并预置黑名单
        list.addAll(PRESET_BLACKLIST);
        return list;
    }

    /**
     * 检查指定包名是否在待关闭列表中
     *
     * @param packageName 应用包名
     * @return 是否需要关闭
     */
    public boolean shouldKill(String packageName) {
        return getKillList().contains(packageName);
    }
}
