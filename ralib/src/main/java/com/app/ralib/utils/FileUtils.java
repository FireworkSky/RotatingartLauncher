package com.app.ralib.utils;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;

    private FileUtils() {}

    /**
     * 递归删除目录及其内容
     * @param path 要删除的路径
     * @return 删除是否成功
     */
    public static boolean deleteDirectoryRecursively(Path path) {
        if (path == null) {
            return false;
        }

        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }

        if (!Files.isReadable(path)) {
            return false;
        }

        AtomicBoolean allDeleted = new AtomicBoolean(true);

        try (var walker = Files.walk(path)) {
            walker
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        if (!deletePathWithRetry(p)) {
                            allDeleted.set(false);
                        }
                    });
        } catch (NoSuchFileException e) {
            return true;
        } catch (AccessDeniedException | SecurityException e) {
            Log.w(TAG, "删除失败（权限）: " + path);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "删除失败: " + path);
            return false;
        } catch (Exception e) {
            Log.w(TAG, "删除失败: " + path, e);
            return false;
        }

        boolean finalSuccess = allDeleted.get() && !Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        return finalSuccess;
    }

    /**
     * 带重试机制的路径删除
     */
    private static boolean deletePathWithRetry(Path path) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    return true;
                }

                Files.delete(path);
                return true;
            } catch (AccessDeniedException | SecurityException e) {
                return false;
            } catch (IOException e) {
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    continue;
                }
                return false;
            } catch (Exception e) {
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    continue;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * 递归删除目录及其内容（File 参数版本）
     * @param directory 要删除的目录
     * @return 删除是否成功
     */
    public static boolean deleteDirectoryRecursively(File directory) {
        if (directory == null || !directory.exists()) {
            return directory == null ? false : true;
        }

        if (!directory.canRead()) {
            return false;
        }

        try {
            Path path = Paths.get(directory.getAbsolutePath()).normalize();
            return deleteDirectoryRecursively(path);
        } catch (Exception e) {
            return false;
        }
    }
}
