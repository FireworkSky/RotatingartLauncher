package com.app.ralaunch.core.importer;

import android.content.Context;
import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
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
            if (listener != null) {
                listener.onComplete(gamePath, modLoaderPath);
            }

        } catch (Exception e) {
            AppLogger.error(TAG, "Import failed: " + e.getMessage(), e);
            if (listener != null) {
                Context context = RaLaunchApplication.getAppContext();
                String errorMsg = context != null ?
                    context.getString(R.string.import_error, e.getMessage()) :
                    "Import failed: " + e.getMessage();
                listener.onError(errorMsg);
            }
        }
    }

    /**
     * 验证文件有效性
     */
    private static boolean validateFiles(ImportTask task, ImportProgressListener listener) {
        Context context = RaLaunchApplication.getAppContext();
        File gameFile = new File(task.getGameFilePath());
        if (!gameFile.exists()) {
            String error = context != null ? 
                context.getString(R.string.import_game_file_not_exist, task.getGameFilePath()) :
                "Game file does not exist: " + task.getGameFilePath();
            AppLogger.error(TAG, error);
            if (listener != null) {
                listener.onError(error);
            }
            return false;
        }

        if (task.hasModLoader()) {
            File modLoaderFile = new File(task.getModLoaderFilePath());
            if (!modLoaderFile.exists()) {
                String error = context != null ? 
                    context.getString(R.string.import_modloader_file_not_exist, task.getModLoaderFilePath()) :
                    "ModLoader file does not exist: " + task.getModLoaderFilePath();
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
            Context context = RaLaunchApplication.getAppContext();
            String error = context != null ? 
                context.getString(R.string.import_storage_insufficient,
                    requiredSpace / 1024.0 / 1024 / 1024,
                    availableSpace / 1024.0 / 1024 / 1024) :
                String.format("Insufficient storage!\nRequired: %.1f GB\nAvailable: %.1f GB",
                    requiredSpace / 1024.0 / 1024 / 1024,
                    availableSpace / 1024.0 / 1024 / 1024);
            AppLogger.error(TAG, error);
            if (listener != null) {
                listener.onError(error);
            }
            return false;
        }

        return true;
    }

    /**
     * 解压游戏文件
     */
    private static String extractGameFiles(ImportTask task, ImportProgressListener listener) {
        if (listener != null) {
            Context context = RaLaunchApplication.getAppContext();
            String message = context != null ?
                context.getString(R.string.import_extracting) :
                "Extracting game files...";
            listener.onProgress(message, 0);
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
            Context context = RaLaunchApplication.getAppContext();
            String message = context != null ?
                context.getString(R.string.import_installing_modloader) :
                "Installing ModLoader...";
            listener.onProgress(message, 80);
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
