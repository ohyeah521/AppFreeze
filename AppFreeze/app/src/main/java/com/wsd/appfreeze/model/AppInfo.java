package com.wsd.appfreeze.model;

import android.graphics.drawable.Drawable;

/**
 * 应用信息模型类
 * 用于在列表中展示用户已安装的应用信息
 */
public class AppInfo {
    /** 应用包名 */
    private final String packageName;
    /** 应用显示名称 */
    private final String appName;
    /** 应用图标 */
    private final Drawable icon;
    /** 是否被用户选中（待机时需要关闭） */
    private boolean selected;

    public AppInfo(String packageName, String appName, Drawable icon, boolean selected) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.selected = selected;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
