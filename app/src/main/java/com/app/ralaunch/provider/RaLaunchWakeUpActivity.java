package com.app.ralaunch.provider;

import android.app.Activity;
import android.os.Bundle;

/**
 * 唤醒活动 - 用于重新注册 DocumentsProvider
 * 
 * 借鉴自 MTDataFilesWakeUpActivity
 * 当系统文件管理器需要重新加载提供器时，会启动此活动
 */
public class RaLaunchWakeUpActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 立即结束，仅用于唤醒应用和 DocumentsProvider
        finish();
    }
}

