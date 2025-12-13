package com.app.ralaunch.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
        AppLogger.info(TAG, "========================================");
        AppLogger.info(TAG, "ProcessLauncherService.launch()");
        AppLogger.info(TAG, "  Assembly: " + assemblyPath);
        AppLogger.info(TAG, "  Args: " + (args != null ? String.join(" ", args) : "(none)"));
        AppLogger.info(TAG, "  StartupHooks: " + (startupHooks != null ? "yes" : "no"));
        AppLogger.info(TAG, "  Title: " + title);
        AppLogger.info(TAG, "========================================");
        
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
        AppLogger.info(TAG, "========================================");
        AppLogger.info(TAG, "ProcessLauncherService Created");
        AppLogger.info(TAG, "Process ID: " + Process.myPid());
        AppLogger.info(TAG, "========================================");
        
       
        try {
            Os.setenv("PACKAGE_NAME", getPackageName(), true);
            
           
            File gameDataDir = new File("/storage/emulated/0/RALauncher");
            if (!gameDataDir.exists()) {
                if (!gameDataDir.mkdirs()) {
                    AppLogger.warn(TAG, "Failed to create game data directory: " + gameDataDir.getAbsolutePath() + ", using files dir as fallback");
                    gameDataDir = getFilesDir();
                } else {
                    AppLogger.info(TAG, "Created game data directory: " + gameDataDir.getAbsolutePath());
                }
            } else {
                AppLogger.info(TAG, "Using game data directory: " + gameDataDir.getAbsolutePath());
            }
            
            String gameDataPath = gameDataDir.getAbsolutePath();
            Os.setenv("HOME", gameDataPath, true);
            Os.setenv("XDG_DATA_HOME", gameDataPath, true);
            Os.setenv("XDG_CONFIG_HOME", gameDataPath, true);
            Os.setenv("XDG_CACHE_HOME", getCacheDir().getAbsolutePath(), true);
        } 
        catch (Exception e) {
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
        String startupHooks = intent.getStringExtra(EXTRA_STARTUP_HOOKS);
        
        if (assemblyPath == null) {
            AppLogger.error(TAG, "Assembly path is null");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // 启动
        launchAsync(assemblyPath, args, startupHooks, title);
        
        return START_STICKY;
    }
    
    private void launchAsync(String assemblyPath, String[] args, String startupHooks, String title) {
        if (mRunning) {
            AppLogger.warn(TAG, "Already running");
            return;
        }
        
        mLauncherThread = new Thread(() -> {
            try {
                mRunning = true;
                updateNotification(title + " 正在运行");
                
                int result = doLaunch(assemblyPath, args, startupHooks);
                
                AppLogger.info(TAG, "Process exited with code: " + result);
            } catch (Exception e) {
                AppLogger.error(TAG, "Launch error: " + e.getMessage(), e);
            } finally {
                mRunning = false;
                stopSelf();
            }
        }, "ProcessLauncher");
        
        mLauncherThread.start();
    }
    
    private int doLaunch(String assemblyPath, String[] args, String startupHooks) {
        try {
            File assemblyFile = new File(assemblyPath);
            String appDir = assemblyFile.getParent();
            String mainAssembly = assemblyFile.getName();
            String dotnetRoot = RuntimePreference.getDotnetRootPath();
            
            AppLogger.info(TAG, "Launching:");
            AppLogger.info(TAG, "  App Dir: " + appDir);
            AppLogger.info(TAG, "  Assembly: " + mainAssembly);
            AppLogger.info(TAG, "  .NET Root: " + dotnetRoot);
            
            // 预加载加密库
            preloadCryptoLibrary(dotnetRoot);
            
            // 设置启动钩子（如果提供）
            if (startupHooks != null && !startupHooks.isEmpty()) {
                GameLauncher.netcorehostSetStartupHooks(startupHooks);
                AppLogger.info(TAG, "  StartupHooks set");
            } else {
                // 自动加载补丁
                setupStartupHooksAuto(assemblyPath);
            }
            
            // 设置参数
            int setResult;
            if (args != null && args.length > 0) {
                AppLogger.info(TAG, "  Args: " + String.join(" ", args));
                setResult = GameLauncher.netcorehostSetParamsWithArgs(
                        appDir, mainAssembly, dotnetRoot, 10, args);
            } else {
                setResult = GameLauncher.netcorehostSetParams(
                        appDir, mainAssembly, dotnetRoot, 10);
            }
            
            if (setResult != 0) {
                AppLogger.error(TAG, "Failed to set params: " + setResult);
                return setResult;
            }
            
            // 启动
            AppLogger.info(TAG, "Launching .NET runtime...");
            return GameLauncher.netcorehostLaunch();
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Launch failed: " + e.getMessage(), e);
            return -1;
        }
    }
    
    /**
     * 预加载加密库
     */
    private void preloadCryptoLibrary(String dotnetRoot) {
        try {
            File sharedDir = new File(dotnetRoot, "shared/Microsoft.NETCore.App");
            if (!sharedDir.exists()) return;
            
            String[] versions = sharedDir.list();
            if (versions == null || versions.length == 0) return;
            
            String cryptoLibPath = dotnetRoot + "/shared/Microsoft.NETCore.App/" + versions[0] + 
                                   "/libSystem.Security.Cryptography.Native.Android.so";
            
            File cryptoLib = new File(cryptoLibPath);
            if (cryptoLib.exists()) {
                AppLogger.info(TAG, "  Preloading crypto library...");
                System.load(cryptoLibPath);
                AppLogger.info(TAG, "  Crypto library loaded");
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to preload crypto: " + e.getMessage());
        }
    }
    
    /**
     * 自动设置启动钩子（根据程序集路径查找补丁）
     */
    private void setupStartupHooksAuto(String assemblyPath) {
        try {

            List<Patch> enabledPatches = RaLaunchApplication.getPatchManager().getEnabledPatches(Paths.get(assemblyPath));
            
            if (!enabledPatches.isEmpty()) {
                String hooks = PatchManager.constructStartupHooksEnvVar(enabledPatches);
                if (hooks != null && !hooks.isEmpty()) {
                    GameLauncher.netcorehostSetStartupHooks(hooks);
                    AppLogger.info(TAG, "  Auto StartupHooks: " + enabledPatches.size() + " patches");
                }
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to setup auto hooks", e);
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
        AppLogger.info(TAG, "ProcessLauncherService Destroyed");
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

