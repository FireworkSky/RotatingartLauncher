package com.app.ralaunch.core.importer;

import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.GameExtractor;
import com.app.ralib.extractors.ExtractorCollection;
import com.app.ralib.extractors.GogShFileExtractor;
import com.app.ralib.extractors.BasicSevenZipExtractor;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 游戏导入服务
 *
 * 负责协调整个游戏导入流程:
 * 1. 验证文件和空间
 * 2. 解压游戏文件
 * 3. 提取游戏信息
 * 4. 处理ModLoader(如果有)
 * 5. 生成配置文件
 */
public class GameImportService {
    private static final String TAG = "GameImportService";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * 异步导入游戏
     *
     * @param task 导入任务
     * @param listener 进度监听器
     */
    public static void importGameAsync(ImportTask task, ImportProgressListener listener) {
        executor.execute(() -> importGame(task, listener));
    }

    /**
     * 同步导入游戏(在后台线程调用)
     *
     * @param task 导入任务
     * @param listener 进度监听器
     */
    private static void importGame(ImportTask task, ImportProgressListener listener) {
        try {
            AppLogger.info(TAG, "开始导入游戏: " + task.getGameName());

            // 1. 验证文件
            if (!validateFiles(task, listener)) {
                return;
            }

            // 2. 检查存储空间
            if (!checkStorage(task, listener)) {
                return;
            }

            // 3. 解压游戏文件
            String gamePath = extractGameFiles(task, listener);
            if (gamePath == null) {
                return;
            }

            // 4. 处理ModLoader(如果有)
            String modLoaderPath = null;
            if (task.hasModLoader()) {
                modLoaderPath = extractModLoader(task, listener);
            }

            // 5. 完成
            AppLogger.info(TAG, "游戏导入完成");
            if (listener != null) {
                listener.onComplete(gamePath, modLoaderPath);
            }

        } catch (Exception e) {
            AppLogger.error(TAG, "导入失败: " + e.getMessage(), e);
            if (listener != null) {
                listener.onError("导入失败: " + e.getMessage());
            }
        }
    }

    /**
     * 验证文件有效性
     */
    private static boolean validateFiles(ImportTask task, ImportProgressListener listener) {
        File gameFile = new File(task.getGameFilePath());
        if (!gameFile.exists()) {
            String error = "游戏文件不存在: " + task.getGameFilePath();
            AppLogger.error(TAG, error);
            if (listener != null) {
                listener.onError(error);
            }
            return false;
        }

        if (task.hasModLoader()) {
            File modLoaderFile = new File(task.getModLoaderFilePath());
            if (!modLoaderFile.exists()) {
                String error = "ModLoader文件不存在: " + task.getModLoaderFilePath();
                AppLogger.error(TAG, error);
                if (listener != null) {
                    listener.onError(error);
                }
                return false;
            }
        }

        return true;
    }

    /**
     * 检查存储空间
     */
    private static boolean checkStorage(ImportTask task, ImportProgressListener listener) {
        File outputDir = new File(task.getOutputDirectory());
        File gameFile = new File(task.getGameFilePath());

        long availableSpace = outputDir.getUsableSpace();
        long requiredSpace = gameFile.length() * 3; // 预留3倍空间

        if (availableSpace < requiredSpace) {
            String error = String.format(
                    "存储空间不足!\n需要约 %.1f GB\n可用 %.1f GB",
                    requiredSpace / 1024.0 / 1024 / 1024,
                    availableSpace / 1024.0 / 1024 / 1024
            );
            AppLogger.error(TAG, error);
            if (listener != null) {
                listener.onError(error);
            }
            return false;
        }

        AppLogger.debug(TAG, String.format("存储空间检查通过: 可用 %.1f GB",
                availableSpace / 1024.0 / 1024 / 1024));
        return true;
    }

    /**
     * 解压游戏文件
     */
    private static String extractGameFiles(ImportTask task, ImportProgressListener listener) {
        if (listener != null) {
            listener.onProgress("正在解压游戏文件...", 0);
        }

        // 使用GameExtractor进行解压
        final String[] resultPath = {null};
        final boolean[] completed = {false};

        GameExtractor.ExtractionListener extractorListener = new GameExtractor.ExtractionListener() {
            @Override
            public void onProgress(String message, int progress) {
                if (listener != null) {
                    listener.onProgress(message, progress);
                }
            }

            @Override
            public void onComplete(String gamePath, String modLoaderPath) {
                resultPath[0] = gamePath;
                completed[0] = true;
            }

            @Override
            public void onError(String error) {
                if (listener != null) {
                    listener.onError(error);
                }
            }
        };

        // 调用解压方法
        GameExtractor.installCompleteGame(
                task.getGameFilePath(),
                task.getModLoaderFilePath(),
                task.getOutputDirectory(),
                extractorListener
        );

        // 等待完成(简化处理,实际应使用更好的同步机制)
        while (!completed[0] && resultPath[0] == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return resultPath[0];
    }

    /**
     * 解压ModLoader
     */
    private static String extractModLoader(ImportTask task, ImportProgressListener listener) {
        if (listener != null) {
            listener.onProgress("正在安装ModLoader...", 80);
        }

        // TODO: 实现ModLoader解压逻辑

        return null;
    }

    /**
     * 关闭服务
     */
    public static void shutdown() {
        executor.shutdown();
    }
}
