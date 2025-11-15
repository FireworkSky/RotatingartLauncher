package com.app.ralaunch.utils;

import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unified logging system for RALaunch
 * Features:
 * - File logging with rotation
 * - Plain text output (no emojis/graphics)
 * - Minimal debug logs
 * - Async writing for performance
 */
public class AppLogger {
    private static final String TAG = "RALaunch";
    private static final boolean ENABLE_DEBUG = false; // Disable debug logs
    private static final boolean ENABLE_FILE_LOGGING = true;

    private static File logFile;
    private static PrintWriter logWriter;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat fileNameFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // Log levels
    public enum Level {
        ERROR(0, "E"),
        WARN(1, "W"),
        INFO(2, "I"),
        DEBUG(3, "D");

        final int priority;
        final String tag;

        Level(int priority, String tag) {
            this.priority = priority;
            this.tag = tag;
        }
    }

    /**
     * Initialize logger with log directory
     */
    public static void init(File logDir) {
        Log.e(TAG, "==================== AppLogger.init() START ====================");
        Log.e(TAG, "ENABLE_FILE_LOGGING: " + ENABLE_FILE_LOGGING);
        Log.e(TAG, "logDir: " + (logDir != null ? logDir.getAbsolutePath() : "NULL"));
        if (!ENABLE_FILE_LOGGING) return;

        try {
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // Create log file with date
            String fileName = "ralaunch_" + fileNameFormat.format(new Date()) + ".log";
            logFile = new File(logDir, fileName);

            // Rotate old logs (keep last 7 days)
            Log.e(TAG, "logWriter created: " + (logWriter != null));
            Log.e(TAG, "logFile path: " + logFile.getAbsolutePath());
            rotateOldLogs(logDir, 7);

            logWriter = new PrintWriter(new FileWriter(logFile, true), true);

            // Initialize native logger
            try {
                initNativeLogger(logDir.getAbsolutePath());
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "Native logger not available: " + e.getMessage());
            }
            Log.e(TAG, "AppLogger.init() completed successfully");

            info("Logger", "Log system initialized: " + logFile.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize file logging", e);
        }
    }

    /**
     * Initialize native logger (JNI)
     */
    private static native void initNativeLogger(String logDir);

    /**
     * Close native logger (JNI)
     */
    private static native void closeNativeLogger();

    static {
        try {
            System.loadLibrary("main");  // 修复：库名称是 main 不是 ralaunch
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Failed to load native logger library: " + e.getMessage());
        }
    }

    /**
     * Rotate old log files
     */
    private static void rotateOldLogs(File logDir, int keepDays) {
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("ralaunch_") && name.endsWith(".log"));
        if (files == null) return;

        long cutoffTime = System.currentTimeMillis() - (keepDays * 24L * 60 * 60 * 1000);

        for (File file : files) {
            if (file.lastModified() < cutoffTime) {
                file.delete();
            }
        }
    }

    /**
     * Close logger and release resources
     */
    public static void close() {
        // 先刷新并关闭文件写入器（同步执行，避免executor已关闭的问题）
        if (logWriter != null) {
            try {
                logWriter.flush();
                logWriter.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close log writer", e);
            }
        }

        // 关闭executor
        try {
            executor.shutdown();
        } catch (Exception e) {
            // Executor可能已经被关闭
        }

        // Close native logger
        try {
            closeNativeLogger();
        } catch (UnsatisfiedLinkError e) {
            // Ignore if native logger not available
        }
    }

    // Logging methods

    public static void error(String tag, String message) {
        log(Level.ERROR, tag, message, null);
    }

    public static void error(String tag, String message, Throwable throwable) {
        log(Level.ERROR, tag, message, throwable);
    }

    public static void warn(String tag, String message) {
        log(Level.WARN, tag, message, null);
    }

    public static void warn(String tag, String message, Throwable throwable) {
        log(Level.WARN, tag, message, throwable);
    }

    public static void info(String tag, String message) {
        log(Level.INFO, tag, message, null);
    }

    public static void debug(String tag, String message) {
        if (ENABLE_DEBUG) {
            log(Level.DEBUG, tag, message, null);
        }
    }

    /**
     * Main logging method
     */
    private static void log(Level level, String tag, String message, Throwable throwable) {
        // Strip emojis and special characters from message
        String cleanMessage = stripEmojis(message);

        // Log to Android logcat (use tag directly without RALaunch prefix)
        switch (level) {
            case ERROR:
                if (throwable != null) {
                    Log.e(tag, cleanMessage, throwable);
                } else {
                    Log.e(tag, cleanMessage);
                }
                break;
            case WARN:
                Log.w(tag, cleanMessage);
                break;
            case INFO:
                Log.i(tag, cleanMessage);
                break;
            case DEBUG:
                if (ENABLE_DEBUG) {
                    Log.d(tag, cleanMessage);
                }
                break;
        }

        // Log to file asynchronously
        if (ENABLE_FILE_LOGGING && logWriter != null) {
            try {
                executor.execute(() -> writeToFile(level, tag, cleanMessage, throwable));
            } catch (Exception e) {
                // Executor已关闭，直接同步写入
                writeToFile(level, tag, cleanMessage, throwable);
            }
        }
    }

    /**
     * Write log entry to file
     */
    private static void writeToFile(Level level, String tag, String message, Throwable throwable) {
        if (logWriter == null) return;

        try {
            String timestamp = dateFormat.format(new Date());
            String logEntry = String.format("[%s] %s/%s: %s",
                timestamp, level.tag, tag, message);

            logWriter.println(logEntry);

            if (throwable != null) {
                logWriter.print("  Exception: ");
                throwable.printStackTrace(logWriter);
            }

            logWriter.flush();

        } catch (Exception e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }

    /**
     * Remove emojis and graphics from text
     */
    private static String stripEmojis(String text) {
        if (text == null) return "";

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Keep only basic printable ASCII and common whitespace
            if ((c >= 32 && c <= 126) || c == '\n' || c == '\r' || c == '\t') {
                result.append(c);
            }
            // Skip all other characters (emojis, special symbols, etc.)
        }

        return result.toString();
    }

    /**
     * Get current log file
     */
    public static File getLogFile() {
        return logFile;
    }


}
