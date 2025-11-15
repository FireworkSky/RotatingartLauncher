package com.app.ralaunch.console;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 控制台消息数据类
 */
public class ConsoleMessage {
    public enum Level {
        INFO,
        WARNING,
        ERROR,
        DEBUG
    }

    private final String message;
    private final Level level;
    private final long timestamp;

    public ConsoleMessage(String message, Level level) {
        this.message = message;
        this.level = level;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage() {
        return message;
    }

    public Level getLevel() {
        return level;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    @Override
    public String toString() {
        String prefix = "";
        switch (level) {
            case ERROR:
                prefix = "";
                break;
            case WARNING:
                prefix = "";
                break;
            case DEBUG:
                prefix = "";
                break;
            case INFO:
            default:
                prefix = "";
                break;
        }
        return String.format("[%s] %s%s", getFormattedTimestamp(), prefix, message);
    }
}

