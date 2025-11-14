package com.app.ralaunch.console;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 控制台服务 - 管理 Console 输出/输入
 * 通过 JNI 重定向 C# Console.WriteLine 和 Console.Read
 */
public class ConsoleService {
    private static final String TAG = "ConsoleService";
    private static ConsoleService instance;

    private final List<ConsoleListener> listeners = new ArrayList<>();
    private final List<ConsoleMessage> messageHistory = new ArrayList<>();
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread outputThread;
    private Thread inputThread;
    
    // 用于 C# Console 重定向的管道
    private PipedWriter consoleWriter;
    private PipedReader consoleReader;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;

    private int messageCount = 0;
    private int errorCount = 0;
    private int warningCount = 0;

    public interface ConsoleListener {
        void onMessageReceived(ConsoleMessage message);
        void onStatisticsUpdated(int totalMessages, int errors, int warnings);
    }

    private ConsoleService() {
        try {
            // 创建输出管道
            consoleWriter = new PipedWriter();
            consoleReader = new PipedReader(consoleWriter);
            printWriter = new PrintWriter(consoleWriter, true);
            bufferedReader = new BufferedReader(consoleReader);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create console pipes", e);
        }
    }

    public static synchronized ConsoleService getInstance() {
        if (instance == null) {
            instance = new ConsoleService();
        }
        return instance;
    }

    /**
     * 启动控制台服务
     */
    public void start() {
        if (isRunning.get()) {
            return;
        }

        isRunning.set(true);

        // 启动输出监听线程
        outputThread = new Thread(() -> {
            while (isRunning.get()) {
                try {
                    String line = bufferedReader.readLine();
                    if (line != null) {
                        processOutputLine(line);
                    }
                } catch (IOException e) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error reading console output", e);
                    }
                }
            }
        }, "ConsoleOutputThread");
        outputThread.start();

        Log.i(TAG, "Console service started");
        logInfo("控制台服务已启动");
    }

    /**
     * 停止控制台服务
     */
    public void stop() {
        isRunning.set(false);

        if (outputThread != null) {
            outputThread.interrupt();
        }
        if (inputThread != null) {
            inputThread.interrupt();
        }

        try {
            if (printWriter != null) printWriter.close();
            if (bufferedReader != null) bufferedReader.close();
            if (consoleWriter != null) consoleWriter.close();
            if (consoleReader != null) consoleReader.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing console pipes", e);
        }

        Log.i(TAG, "Console service stopped");
    }

    /**
     * 处理输出行
     */
    private void processOutputLine(String line) {
        ConsoleMessage.Level level = ConsoleMessage.Level.INFO;

        // 简单的日志级别检测
        String lowerLine = line.toLowerCase();
        if (lowerLine.contains("error") || lowerLine.contains("exception") || lowerLine.contains("failed")) {
            level = ConsoleMessage.Level.ERROR;
            errorCount++;
        } else if (lowerLine.contains("warn") || lowerLine.contains("warning")) {
            level = ConsoleMessage.Level.WARNING;
            warningCount++;
        } else if (lowerLine.contains("debug")) {
            level = ConsoleMessage.Level.DEBUG;
        }

        ConsoleMessage message = new ConsoleMessage(line, level);
        messageCount++;

        // 保存到历史（限制最多 1000 条）
        synchronized (messageHistory) {
            messageHistory.add(message);
            if (messageHistory.size() > 1000) {
                messageHistory.remove(0);
            }
        }

        // 通知监听器
        notifyMessageReceived(message);
        notifyStatisticsUpdated();
    }

    /**
     * 从 C# 接收输出（通过 JNI 调用）
     */
    public void writeOutput(String text) {
        if (printWriter != null) {
            printWriter.println(text);
        }
    }

    /**
     * 向 C# 发送输入（通过 JNI 回调）
     */
    public void sendInput(String input) {
        inputQueue.offer(input);
        Log.d(TAG, "Input sent: " + input);
    }

    /**
     * C# Console.ReadLine 调用此方法获取输入（阻塞）
     */
    public String readInput() throws InterruptedException {
        Log.d(TAG, "Waiting for input...");
        String input = inputQueue.take();
        Log.d(TAG, "Input received: " + input);
        return input;
    }

    /**
     * 添加监听器
     */
    public void addListener(ConsoleListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * 移除监听器
     */
    public void removeListener(ConsoleListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyMessageReceived(ConsoleMessage message) {
        synchronized (listeners) {
            for (ConsoleListener listener : listeners) {
                listener.onMessageReceived(message);
            }
        }
    }

    private void notifyStatisticsUpdated() {
        synchronized (listeners) {
            for (ConsoleListener listener : listeners) {
                listener.onStatisticsUpdated(messageCount, errorCount, warningCount);
            }
        }
    }

    /**
     * 获取消息历史
     */
    public List<ConsoleMessage> getMessageHistory() {
        synchronized (messageHistory) {
            return new ArrayList<>(messageHistory);
        }
    }

    /**
     * 清除消息历史
     */
    public void clearHistory() {
        synchronized (messageHistory) {
            messageHistory.clear();
        }
        messageCount = 0;
        errorCount = 0;
        warningCount = 0;
        notifyStatisticsUpdated();
    }

    /**
     * 便捷方法：记录信息
     */
    public void logInfo(String message) {
        writeOutput(message);
    }

    /**
     * 便捷方法：记录错误
     */
    public void logError(String message) {
        writeOutput("[ERROR] " + message);
    }

    /**
     * 便捷方法：记录警告
     */
    public void logWarning(String message) {
        writeOutput("[WARN] " + message);
    }

    /**
     * 获取统计信息
     */
    public int getMessageCount() {
        return messageCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    // ============== Native 方法 ==============
    // 这些方法需要在 C++ 端实现，用于重定向 C# Console

    /**
     * 设置 C# Console 输出重定向回调
     */
    public static native void nativeSetConsoleOutputCallback();

    /**
     * 设置 C# Console 输入重定向回调
     */
    public static native void nativeSetConsoleInputCallback();
}

