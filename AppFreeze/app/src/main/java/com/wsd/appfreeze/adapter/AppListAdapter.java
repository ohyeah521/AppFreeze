package com.wsd.appfreeze.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wsd.appfreeze.R;
import com.wsd.appfreeze.model.AppInfo;

import java.util.List;

/**
 * 应用列表适配器
 * 用于在 RecyclerView 中展示用户应用列表，支持勾选操作。
 * 适配 Android TV 遥控器焦点导航。
 */
public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private final List<AppInfo> appList;

    public AppListAdapter(List<AppInfo> appList) {
        this.appList = appList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo appInfo = appList.get(position);

        holder.ivIcon.setImageDrawable(appInfo.getIcon());
        holder.tvAppName.setText(appInfo.getAppName());
        holder.tvPackageName.setText(appInfo.getPackageName());
        holder.cbSelected.setChecked(appInfo.isSelected());

        // 整行可点击，点击切换选中状态（适配TV遥控器确认键）
        holder.itemView.setOnClickListener(v -> {
            appInfo.setSelected(!appInfo.isSelected());
            holder.cbSelected.setChecked(appInfo.isSelected());
        });

        // 确保列表项可以获取焦点（TV遥控器导航）
        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvAppName;
        TextView tvPackageName;
        CheckBox cbSelected;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvPackageName = itemView.findViewById(R.id.tv_package_name);
            cbSelected = itemView.findViewById(R.id.cb_selected);
        }
    }
}
