package com.wsd.appfreeze;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wsd.appfreeze.adapter.AppListAdapter;
import com.wsd.appfreeze.model.AppInfo;
import com.wsd.appfreeze.service.AppFreezeService;
import com.wsd.appfreeze.util.FreezeConfig;
import com.wsd.appfreeze.util.PermissionHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主界面 - 显示用户应用列表，供用户勾选待机时需要关闭的应用。
 * 启动时检测 ADB 连接状态，未连接时引导用户开启无线调试。
 */
public class MainActivity extends FragmentActivity {

    private static final String TAG = "MainActivity";

    private RecyclerView rvAppList;
    private TextView tvHint;
    private TextView tvPermissionStatus;
    private Button btnSelectAll;
    private Button btnDeselectAll;
    private Button btnSave;
    private Button btnAdbGuide;
    private Button btnRecheck;

    private AppListAdapter adapter;
    private final List<AppInfo> appList = new ArrayList<>();
    private FreezeConfig freezeConfig;
    private boolean isAdbAvailable = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        freezeConfig = new FreezeConfig(this);

        initViews();
        setupListeners();
        checkAdbConnection();
        loadUserApps();

        // 启动前台服务
        startAppFreezeService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void initViews() {
        rvAppList = findViewById(R.id.rv_app_list);
        tvHint = findViewById(R.id.tv_hint);
        tvPermissionStatus = findViewById(R.id.tv_permission_status);
        btnSelectAll = findViewById(R.id.btn_select_all);
        btnDeselectAll = findViewById(R.id.btn_deselect_all);
        btnSave = findViewById(R.id.btn_save);
        btnAdbGuide = findViewById(R.id.btn_adb_guide);
        btnRecheck = findViewById(R.id.btn_recheck);

        rvAppList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(appList);
        rvAppList.setAdapter(adapter);
    }

    private void setupListeners() {
        btnSelectAll.setOnClickListener(v -> {
            for (AppInfo app : appList) app.setSelected(true);
            adapter.notifyDataSetChanged();
        });

        btnDeselectAll.setOnClickListener(v -> {
            for (AppInfo app : appList) app.setSelected(false);
            adapter.notifyDataSetChanged();
        });

        btnSave.setOnClickListener(v -> saveConfig());
        btnAdbGuide.setOnClickListener(v -> showAdbGuideDialog());

        btnRecheck.setOnClickListener(v -> {
            tvPermissionStatus.setText(R.string.loading_apps);
            tvPermissionStatus.setTextColor(0xFFAAAAAA);
            checkAdbConnection();
        });
    }

    /**
     * 异步检测 ADB 连接状态（涉及网络IO，不能在主线程）
     */
    private void checkAdbConnection() {
        executor.execute(() -> {
            boolean available = PermissionHelper.isAdbAvailable(this);
            mainHandler.post(() -> {
                isAdbAvailable = available;
                updatePermissionUI();
                if (available) {
                    Toast.makeText(this, R.string.recheck_result_granted, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void updatePermissionUI() {
        if (isAdbAvailable) {
            tvPermissionStatus.setText(R.string.permission_granted);
            tvPermissionStatus.setTextColor(0xFF4CAF50);
        } else {
            tvPermissionStatus.setText(R.string.permission_not_granted);
            tvPermissionStatus.setTextColor(0xFFFF9800);
        }
    }

    private void showAdbGuideDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_adb_guide, null);
        TextView tvStatus = dialogView.findViewById(R.id.tv_permission_status);
        if (isAdbAvailable) {
            tvStatus.setText(R.string.permission_granted);
            tvStatus.setTextColor(0xFF4CAF50);
        } else {
            tvStatus.setText(R.string.permission_not_granted);
            tvStatus.setTextColor(0xFFFF9800);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.adb_guide_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    private void loadUserApps() {
        tvHint.setText(R.string.loading_apps);
        executor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            String selfPackage = getPackageName();
            Set<String> savedKillList = freezeConfig.getKillList();
            List<AppInfo> result = new ArrayList<>();

            for (ApplicationInfo appInfo : installedApps) {
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                if (appInfo.packageName.equals(selfPackage)) continue;
                if (appInfo.packageName.contains("com.sony.")) continue;

                String appName = pm.getApplicationLabel(appInfo).toString();
                boolean isSelected = savedKillList.contains(appInfo.packageName);
                result.add(new AppInfo(appInfo.packageName, appName,
                        pm.getApplicationIcon(appInfo), isSelected));
            }

            result.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

            mainHandler.post(() -> {
                appList.clear();
                appList.addAll(result);
                adapter.notifyDataSetChanged();
                tvHint.setText(result.isEmpty() ? getString(R.string.no_user_apps)
                        : getString(R.string.hint_select_apps));
                Log.i(TAG, "已加载 " + result.size() + " 个用户应用");
            });
        });
    }

    private void saveConfig() {
        Set<String> killList = new HashSet<>();
        for (AppInfo app : appList) {
            if (app.isSelected()) killList.add(app.getPackageName());
        }
        freezeConfig.saveKillList(killList);
        String msg = String.format(getString(R.string.save_success), killList.size());
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.i(TAG, msg);
    }

    private void startAppFreezeService() {
        Intent serviceIntent = new Intent(this, AppFreezeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Log.i(TAG, "AppFreezeService 启动指令已发送");
    }
}
