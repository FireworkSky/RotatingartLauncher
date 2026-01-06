package com.app.ralaunch.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Process;
import android.system.Os;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.core.GameLauncher;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.RuntimePreference;
import com.app.ralib.patch.Patch;
import com.app.ralib.patch.PatchManager;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 通用进程启动服务 - 在独立进程中启动 .NET 程序集
 * 
 * 此服务运行在独立的进程中（android:process=":launcher"），
 * 
 * C# 补丁通过 native 层传递所有参数：
 * - 程序集路径
 * - 命令行参数
 * - 可选的启动钩子
 */
public class ProcessLauncherService extends Service {
    private static final String TAG = "ProcessLauncher";
    private static final String CHANNEL_ID = "process_launcher_channel";
    private static final int NOTIFICATION_ID = 9528;
    
    // Intent Extras - 通用参数
    public static final String EXTRA_ASSEMBLY_PATH = "assembly_path";
    public static final String EXTRA_ARGS = "args";
    public static final String EXTRA_STARTUP_HOOKS = "startup_hooks";
    public static final String EXTRA_TITLE = "title";
    
    // 进程状态
    private boolean mRunning = false;
    private Thread mLauncherThread;
    
    /**
     * 从 native 层调用：启动进程服务
     * 
     * @param context 应用上下文
     * @param assemblyPath 程序集完整路径
     * @param args 命令行参数数组
     * @param startupHooks 启动钩子（DOTNET_STARTUP_HOOKS 值，可为 null）
     * @param title 通知标题
     */
    public static void launch(Context context, String assemblyPath, String[] args, 
                             String startupHooks, String title) {
        Intent intent = new Intent(context, ProcessLauncherService.class);
        intent.putExtra(EXTRA_ASSEMBLY_PATH, assemblyPath);
        intent.putExtra(EXTRA_ARGS, args);
        intent.putExtra(EXTRA_STARTUP_HOOKS, startupHooks);
        intent.putExtra(EXTRA_TITLE, title != null ? title : "Process");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    /**
     * 停止进程服务
     */
    public static void stop(Context context) {
        context.stopService(new Intent(context, ProcessLauncherService.class));
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Os.setenv("PACKAGE_NAME", getPackageName(), true);
            Os.setenv("EXTERNAL_STORAGE_DIRECTORY", Environment.getExternalStorageDirectory().getPath(), true);
            
            File gameDataDir = Paths.get(Environment.getExternalStorageDirectory().getPath(), "RALauncher").toFile();
            if (!gameDataDir.exists()) {
                if (!gameDataDir.mkdirs()) {
                    gameDataDir = getFilesDir();
                }
            }
            
            String gameDataPath = gameDataDir.getAbsolutePath();
            Os.setenv("HOME", gameDataPath, true);
            Os.setenv("XDG_DATA_HOME", gameDataPath, true);
            Os.setenv("XDG_CONFIG_HOME", gameDataPath, true);
            Os.setenv("XDG_CACHE_HOME", getCacheDir().getAbsolutePath(), true);
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to set environment", e);
        }
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            AppLogger.error(TAG, "Intent is null");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        String title = intent.getStringExtra(EXTRA_TITLE);
        startForeground(NOTIFICATION_ID, createNotification(title + " 正在启动..."));
        
        // 获取参数
        String assemblyPath = intent.getStringExtra(EXTRA_ASSEMBLY_PATH);
        String[] args = intent.getStringArrayExtra(EXTRA_ARGS);
//        String startupHooks = intent.getStringExtra(EXTRA_STARTUP_HOOKS); // ignored, use auto setup
        
        if (assemblyPath == null) {
            AppLogger.error(TAG, "Assembly path is null");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // 启动
        launchAsync(assemblyPath, args, title);
        
        return START_STICKY;
    }
    
    private void launchAsync(String assemblyPath, String[] args, String title) {
        if (mRunning) {
            return;
        }
        
        mLauncherThread = new Thread(() -> {
            try {
                mRunning = true;
                updateNotification(title + " 正在运行");
                
                doLaunch(assemblyPath, args);
            } catch (Exception e) {
                AppLogger.error(TAG, "Launch error: " + e.getMessage(), e);
            } finally {
                mRunning = false;
                stopSelf();
            }
        }, "ProcessLauncher");
        
        mLauncherThread.start();
    }
    
    private int doLaunch(String assemblyPath, String[] args) {
        try {
            return GameLauncher.INSTANCE.launchDotNetAssembly(
                    assemblyPath,
                    args,
                    RaLaunchApplication.getPatchManager().getEnabledPatches(Paths.get(assemblyPath)));
        } catch (Exception e) {
            AppLogger.error(TAG, "Launch failed: " + e.getMessage(), e);
            AppLogger.error(TAG, "Last Error Msg: " + GameLauncher.INSTANCE.getLastErrorMessage());
            return -1;
        }
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        mRunning = false;
        if (mLauncherThread != null && mLauncherThread.isAlive()) {
            mLauncherThread.interrupt();
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "进程启动器", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("后台进程运行通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("后台进程")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_ral)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
    
    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(text));
        }
    }
}

