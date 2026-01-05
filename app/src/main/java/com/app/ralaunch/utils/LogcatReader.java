package com.app.ralaunch.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logcat 读取器
 *
 * 从 logcat 捕获日志并保存到文件
 * 支持过滤特定 tag 和日志级别
 */
public class LogcatReader {
    private static final String TAG = "LogcatReader";

    private static LogcatReader instance;
    private Thread readerThread;
    private Process logcatProcess;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private File logFile;
    private PrintWriter logWriter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    // 需要过滤掉的系统标签（黑名单）
    private static final String[] SYSTEM_TAG_BLACKLIST = {
        "ScrollerOptimizationManager",
        "HWUI",
        "NativeTurboSchedManager",
        "TurboSchedMonitor",
        "MiuiMultiWindowUtils",
        "MiuiProcessManagerImpl",
        "FramePredict",
        "FirstFrameSpeedUp",
        "InsetsController",
        "ViewRootImpl",
        "Choreographer",
        "HandWritingStubImpl",
        "ViewRootImplStubImpl",
        "CompatChangeReporter",
        "ContentCatcher",
        "SecurityManager",
        "ComputilityLevel",
        "Activity",
        "libc",
        "SplineOverScroller",
        "BufferQueueProducer",
        "BLASTBufferQueue",
        // OPlus
        "oplus.android.OplusFrameworkFactoryImpl",
        "DynamicFramerate",
        "OplusViewDragTouchViewHelper"
    };

    // 日志回调接口
    public interface LogCallback {
        void onLogReceived(String tag, String level, String message);
    }

    private LogCallback callback;
    private Handler mainHandler;

    private LogcatReader() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized LogcatReader getInstance() {
        if (instance == null) {
            instance = new LogcatReader();
        }
        return instance;
    }

    /**
     * 设置日志回调
     */
    public void setCallback(LogCallback callback) {
        this.callback = callback;
    }

    /**
     * 启动 logcat 读取
     *
     * @param logDir 日志保存目录
     * @param filterTags 要过滤的 tag 数组，如 {"GameLauncher", "TModLoaderPatch", "StartupHook"}
     */
    public void start(File logDir, String[] filterTags) {
        if (running.get()) {
            Log.w(TAG, "LogcatReader already running");
            return;
        }

        try {
            // 创建日志文件
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            String fileName = "ralaunch_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".log";
            logFile = new File(logDir, fileName);
            logWriter = new PrintWriter(new FileWriter(logFile, true), true);

            Log.i(TAG, "LogcatReader started, logging to: " + logFile.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Failed to create log file", e);
            return;
        }

        running.set(true);

        readerThread = new Thread(() -> {
            try {
                // 先清除旧的 logcat 缓冲区
                Runtime.getRuntime().exec("logcat -c").waitFor();

                // 构建 logcat 命令
                StringBuilder cmd = new StringBuilder("logcat -v time");

                // 添加过滤器
                if (filterTags != null && filterTags.length > 0) {
                    // 静默所有，只显示指定 tag
                    cmd.append(" *:S");
                    for (String tag : filterTags) {
                        cmd.append(" ").append(tag).append(":V");
                    }
                }

                logcatProcess = Runtime.getRuntime().exec(cmd.toString());
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream())
                );

                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    processLogLine(line);
                }

                reader.close();

            } catch (Exception e) {
                if (running.get()) {
                    Log.e(TAG, "LogcatReader error", e);
                }
            }
        }, "LogcatReader");

        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * 启动 logcat 读取（捕获所有应用日志，不过滤）
     */
    public void start(File logDir) {
        // 不过滤 tag，捕获所有日志
        start(logDir, null);
    }

    /**
     * 检查标签是否应该被过滤掉
     */
    private boolean shouldFilterTag(String tag) {
        if (tag == null) return true;

        // 检查是否在黑名单中
        for (String blacklistedTag : SYSTEM_TAG_BLACKLIST) {
            if (tag.contains(blacklistedTag)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 处理 logcat 行
     */
    private void processLogLine(String line) {
        if (line == null || line.isEmpty()) return;

        // 过滤系统分隔符
        if (line.startsWith("---------")) {
            return;
        }

        try {
            // 解析 logcat -v time 格式: "MM-DD HH:MM:SS.mmm D/Tag(PID): message"
            // 例如: "11-19 10:30:45.123 I/GameLauncher(12345): Starting game"

            // 找到时间戳结束位置（格式：MM-DD HH:MM:SS.mmm）
            if (line.length() < 18) return;

            String timestamp = line.substring(0, 18).trim();

            // 找到级别和标签的起始位置
            int levelStart = 18;
            while (levelStart < line.length() && line.charAt(levelStart) == ' ') {
                levelStart++;
            }

            if (levelStart >= line.length()) return;

            // 找到 '/' 分隔符（级别和标签之间）
            int tagStart = line.indexOf('/', levelStart);
            if (tagStart < 0) return;

            String level = line.substring(levelStart, tagStart).trim();

            // 找到 ':' 分隔符（标签和消息之间）
            int messageStart = line.indexOf(':', tagStart);
            if (messageStart < 0) return;

            // 提取标签（去掉可能的 PID）
            String tagWithPid = line.substring(tagStart + 1, messageStart).trim();
            String tag = tagWithPid;

            // 移除 PID，如 "GameLauncher(12345)" -> "GameLauncher"
            int pidStart = tagWithPid.indexOf('(');
            if (pidStart > 0) {
                tag = tagWithPid.substring(0, pidStart).trim();
            }

            // 检查是否应该过滤此标签
            if (shouldFilterTag(tag)) {
                return;
            }

            // 提取消息内容
            String message = messageStart + 1 < line.length() ? line.substring(messageStart + 1).trim() : "";

            // 格式化日志行: [时间] [级别] [标签] 消息
            String formattedLine = String.format("[%s] [%s] [%s] %s",
                timestamp, level, tag, message);

            // 写入文件
            if (logWriter != null) {
                logWriter.println(formattedLine);
                logWriter.flush();
            }

            // 回调
            if (callback != null) {
                final String fTag = tag;
                final String fLevel = level;
                final String fMessage = message;
                mainHandler.post(() -> callback.onLogReceived(fTag, fLevel, fMessage));
            }

        } catch (Exception e) {
            // 解析失败，忽略
            // Log.w(TAG, "Failed to parse log line: " + line, e);
        }
    }

    /**
     * 停止 logcat 读取
     */
    public void stop() {
        running.set(false);

        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        if (logWriter != null) {
            logWriter.flush();
            logWriter.close();
            logWriter = null;
        }

        Log.i(TAG, "LogcatReader stopped");
    }

    /**
     * 获取当前日志文件
     */
    public File getLogFile() {
        return logFile;
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}
