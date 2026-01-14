package com.app.ralaunch.utils;

import android.nfc.Tag;
import android.util.Log;

import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.data.SettingsManager;

import java.io.File;

/**
 * Unified logging system for RALaunch
 * Features:
 * - All logs output to logcat
 * - LogcatReader captures logcat and saves to file
 * - Plain text output (no emojis/graphics)
 * - Minimal debug logs
 */
public class AppLogger {
    private static final String TAG = "RALaunch";
    private static final boolean ENABLE_DEBUG = false; // Disable debug logs

    private static LogcatReader logcatReader;
    private static File logDir;
    private static boolean initialized = false;

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
     * Starts LogcatReader to capture all logs and save to file
     */
    public static void init(File logDirectory) {
        if (initialized) {
            Log.w(TAG, "AppLogger already initialized");
            return;
        }

        logDir = logDirectory;
        Log.i(TAG, "==================== AppLogger.init() START ====================");
        Log.i(TAG, "logDir: " + (logDir != null ? logDir.getAbsolutePath() : "NULL"));

        try {
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // Start LogcatReader to capture all logs
            logcatReader = LogcatReader.getInstance();
            var settingsManager = SettingsManager.getInstance();
            if (settingsManager.isLogSystemEnabled()) {
                logcatReader.start(logDir);
                Log.i(TAG, "LogcatReader started");
            }
            else {
                Log.w(TAG, "LogcatReader not started - logging disabled in settings");
            }

            initialized = true;
            Log.i(TAG, "AppLogger.init() completed");
            info("Logger", "Log system initialized: " + logDir.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize logging", e);
        }
    }

    /**
     * Close logger and release resources
     */
    public static void close() {
        // Stop LogcatReader
        if (logcatReader != null) {
            logcatReader.stop();
            logcatReader = null;
        }

        initialized = false;
        Log.i(TAG, "AppLogger closed");
    }

    // Logging methods - all output to logcat only
    // LogcatReader will capture and save to file

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
     * Main logging method - outputs to logcat only
     * LogcatReader captures and saves to file
     */
    private static void log(Level level, String tag, String message, Throwable throwable) {
        // Log to Android logcat only
        switch (level) {
            case ERROR:
                if (throwable != null) {
                    Log.e(tag, message, throwable);
                } else {
                    Log.e(tag, message);
                }
                break;
            case WARN:
                Log.w(tag, message);
                break;
            case INFO:
                Log.i(tag, message);
                break;
            case DEBUG:
                if (ENABLE_DEBUG) {
                    Log.d(tag, message);
                }
                break;
        }
    }

    /**
     * Get current log file from LogcatReader
     */
    public static File getLogFile() {
        return logcatReader != null ? logcatReader.getLogFile() : null;
    }

    /**
     * Get LogcatReader instance for setting callbacks
     */
    public static LogcatReader getLogcatReader() {
        return logcatReader;
    }
}
