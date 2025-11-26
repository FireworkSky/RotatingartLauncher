package com.app.ralaunch.settings;

import android.view.View;
import androidx.fragment.app.Fragment;

/**
 * 设置模块基类
 * 所有设置模块都应该实现此接口
 */
public interface SettingsModule {
    /**
     * 初始化设置模块
     * @param fragment Fragment 实例
     * @param rootView 根视图
     */
    void setup(Fragment fragment, View rootView);
}


