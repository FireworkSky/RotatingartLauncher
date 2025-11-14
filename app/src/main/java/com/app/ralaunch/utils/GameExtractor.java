package com.app.ralaunch.utils;

import android.util.Log;

import com.app.ralib.extractors.BasicSevenZipExtractor;
import com.app.ralib.extractors.ExtractorCollection;
import com.app.ralib.extractors.GogShFileExtractor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

/**
 * 游戏解压器
 *
 * 提供完整的游戏包解压功能，支持：
 * - ZIP 压缩包解压
 * - 7-Zip (7z) 压缩包解压
 * - 自动识别并提取游戏文件和 ModLoader
 * - 进度回调和错误处理
 * - 游戏信息提取和配置生成
 *
 * 使用 SevenZipJBinding 库处理复杂压缩格式
 */
public class GameExtractor {
    private static final String TAG = "GameExtractor";

    public interface ExtractionListener {
        void onProgress(String message, int progress);
        void onComplete(String gamePath, String modLoaderPath);
        void onError(String error);
    }

    /**
     * 检查可用存储空间
     * @param outputDir 目标目录
     * @param inputFile 输入文件
     * @param listener 监听器
     * @return 是否有足够空间
     */
    private static boolean checkAvailableSpace(File outputDir, File inputFile, ExtractionListener listener) {
        long availableSpace = outputDir.getUsableSpace();
        long requiredSpace = inputFile.length() * 3; // 预留3倍空间用于解压

        Log.d(TAG, String.format("Space check: available=%.1f GB, required=%.1f GB",
                availableSpace / 1024.0 / 1024 / 1024,
                requiredSpace / 1024.0 / 1024 / 1024));

        if (availableSpace < requiredSpace) {
            String errorMsg = String.format(
                    "存储空间不足！\n需要约 %.1f GB\n可用 %.1f GB\n\n请释放更多空间后重试",
                    requiredSpace / 1024.0 / 1024 / 1024,
                    availableSpace / 1024.0 / 1024 / 1024
            );
            Log.e(TAG, errorMsg);
            if (listener != null) {
                listener.onError(errorMsg);
            }
            return false;
        }
        return true;
    }

    public static void installCompleteGame(String shFilePath, String modLoaderZipPath,
                                           String outputDir, ExtractionListener listener) {
        try {
            var extractionListener = new ExtractorCollection.ExtractionListener() {
                @Override
                public void onProgress(String message, float progress, HashMap<String, Object> state) {
                    if (listener != null) {
                        var extractorIndex = (int)state.get(ExtractorCollection.STATE_KEY_EXTRACTOR_INDEX); // Im sure it wont be null
                        if (extractorIndex == 0) { // GogShFileExtractor
                            listener.onProgress(message, (int)(progress*0.7f*100));
                        } else if (extractorIndex == 1) { // BasicSevenZipExtractor
                            listener.onProgress(message, (int)((0.7f+progress*0.3f)*100));
                        } else {
                            Log.w(TAG, "Unknown extractor index: " + extractorIndex);
                        }
                    }
                }

                @Override
                public void onError(String message, Exception ex, HashMap<String, Object> state) {
                    if (listener != null) {
                        listener.onError(message);
                    }
                }

                @Override
                public void onComplete(String message, HashMap<String, Object> state) {
                    if (listener != null) {
                        var extractorIndex = (int)state.get(ExtractorCollection.STATE_KEY_EXTRACTOR_INDEX); // Im sure it wont be null
                        if (extractorIndex == 0) {
                            // Do nothing here

                        } else if (extractorIndex == 1) {
                            var gamePath = (Path)state.get(GogShFileExtractor.STATE_KEY_GAME_PATH);
                            if (gamePath == null) {
                                Log.e(TAG, "Game path is null in state");
                                listener.onError("无法获取游戏路径");
                                return;
                            }
                            var modLoaderPath = Paths.get(outputDir, "GoG Games", "ModLoader");
                            listener.onComplete(gamePath.toString(), modLoaderPath.toString());
                        } else {
                            Log.w(TAG, "Unknown extractor index: " + extractorIndex);
                        }
                    }
                }
            };

            new ExtractorCollection.Builder()
                    .addExtractor(new GogShFileExtractor(
                            Paths.get(shFilePath),
                            Paths.get(outputDir),
                            extractionListener
                    ))
                    .addExtractor(new BasicSevenZipExtractor(
                            Paths.get(modLoaderZipPath),
                            getProperExtractionPrefixForModLoaderZip(Paths.get(modLoaderZipPath)),
                            Paths.get(outputDir, "GoG Games", "ModLoader"), // for simplicity
                            extractionListener
                    ))
                    .build()
                    .extractAllInNewThread();
        } catch (Exception e) {
            Log.e(TAG, "Complete installation failed", e);
            if (listener != null) {
                listener.onError("安装失败: " + e.getMessage());
            }
        }
    }

    /**
     * 只安装纯游戏（不安装 ModLoader）
     */
    public static void installGameOnly(String shFilePath, String outputDir, ExtractionListener listener) {
        var extractionListener = new ExtractorCollection.ExtractionListener() {
            @Override
            public void onProgress(String message, float progress, HashMap<String, Object> state) {
                if (listener != null) {
                    var extractorIndex = (int)state.get(ExtractorCollection.STATE_KEY_EXTRACTOR_INDEX); // Im sure it wont be null
                    if (extractorIndex == 0) { // GogShFileExtractor
                        listener.onProgress(message, (int)(progress*100));
                    } else {
                        Log.w(TAG, "Unknown extractor index: " + extractorIndex);
                    }
                }
            }

            @Override
            public void onError(String message, Exception ex, HashMap<String, Object> state) {
                if (listener != null) {
                    listener.onError(message);
                }
            }

            @Override
            public void onComplete(String message, HashMap<String, Object> state) {
                if (listener != null) {
                    var extractorIndex = (int)state.get(ExtractorCollection.STATE_KEY_EXTRACTOR_INDEX); // Im sure it wont be null
                    if (extractorIndex == 0) {
                        var gamePath = (Path)state.get(GogShFileExtractor.STATE_KEY_GAME_PATH);
                        if (gamePath == null) {
                            Log.e(TAG, "Game path is null in state");
                            listener.onError("无法获取游戏路径");
                            return;
                        }
                        listener.onComplete(gamePath.toString(), null);
                    } else {
                        Log.w(TAG, "Unknown extractor index: " + extractorIndex);
                    }
                }
            }
        };

        new ExtractorCollection.Builder()
                .addExtractor(new GogShFileExtractor(
                        Paths.get(shFilePath),
                        Paths.get(outputDir),
                        extractionListener
                ))
                .build()
                .extractAllInNewThread();
    }

    /**
     * 分析 ModLoader ZIP 文件结构，确定正确的解压前缀路径
     *
     * @param zipFilePath ModLoader ZIP 文件路径
     * @return 适合解压的前缀路径
     */
    public static Path getProperExtractionPrefixForModLoaderZip(Path zipFilePath) {
        try (ZipFile zip = new ZipFile(zipFilePath.toFile())) {
            List<String> rootEntries = Arrays.asList(zip.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> {
                        // Filter logic for root directory entries
                        return !name.contains("/") || name.lastIndexOf('/') == name.length() - 1;
                    })
                    .toArray(String[]::new));


            if (rootEntries.size() == 1) {
                return Paths.get(rootEntries.get(0));
            } else if (rootEntries.contains("ModLoader/")) {
                return Paths.get("ModLoader");
            } else if (rootEntries.contains("tModLoader/")) {
                return Paths.get("tModLoader");
            } else if (rootEntries.contains("SMAPI/")) {
                return Paths.get("SMAPI");
            } else {
                // Check for SMAPI with version number directory
                for (String entry : rootEntries) {
                    if (entry.matches("^SMAPI [\\d\\.]+ installer/$")) {
                        return Paths.get(entry);
                    }
                }
            }

            // unknown structure
            return Paths.get("");
        } catch (IOException e) {
            Log.e(TAG, "Failed to analyze ModLoader ZIP structure", e);
            return Paths.get("");
        }
    }


    /**
     * 检测并配置 SMAPI（星露谷物语模组加载器）
     *
     * @param context Android 上下文
     * @param gameDir 游戏目录
     * @return 包含 [modLoaderPath, gameBodyPath] 的数组，如果不是 SMAPI 则返回 null
     */
    public static String[] detectAndConfigureSMAPI(android.content.Context context, File gameDir) {
        try {
            // 检查是否需要运行 SMAPI 安装器
            File installerDll = findSMAPIInstallerDll(gameDir);
            if (installerDll != null && installerDll.exists()) {
                Log.i(TAG, "🔧 检测到 SMAPI 安装器: " + installerDll.getAbsolutePath());

                // 检查是否已安装 SMAPI
                boolean smapiInstalled = checkSMAPIInstalled(gameDir);
                if (!smapiInstalled) {
                    Log.i(TAG, "📦 SMAPI 尚未安装，准备运行安装器...");
                    // 运行 SMAPI 安装器（通过 dotnet_host）
                    runSMAPIInstaller(context, installerDll, gameDir);
                } else {
                    Log.d(TAG, "[OK] SMAPI 已安装");
                }
            }

            // 检查已安装的 SMAPI
            // SMAPI 可能的位置：
            // 1. 直接在游戏目录下
            // 2. 在 internal/linux/ 子目录中

            File[] searchDirs = {
                    gameDir,                                          // 直接在根目录
                    new File(gameDir, "internal/linux"),             // Linux SMAPI 结构
                    new File(gameDir, "internal")                    // 其他可能结构
            };

            for (File searchDir : searchDirs) {
                if (!searchDir.exists() || !searchDir.isDirectory()) {
                    continue;
                }

                // 检查 SMAPI 标志文件
                File smapiExe = new File(searchDir, "StardewModdingAPI.exe");
                File smapiDll = new File(searchDir, "StardewModdingAPI.dll");
                File gameExe = new File(searchDir, "Stardew Valley.exe");
                File gameDll = new File(searchDir, "Stardew Valley.dll");

                // 检查是否存在 SMAPI
                boolean hasSMAPI = smapiExe.exists() || smapiDll.exists();
                boolean hasGameBody = gameExe.exists() || gameDll.exists();

                if (hasSMAPI && hasGameBody) {
                    Log.d(TAG, "[OK] 检测到 SMAPI (星露谷物语模组加载器)");
                    Log.d(TAG, "  检测位置: " + searchDir.getAbsolutePath());

                    // 确定 SMAPI 启动器路径（优先使用 .dll）
                    String smapiPath = smapiDll.exists() ?
                            smapiDll.getAbsolutePath() : smapiExe.getAbsolutePath();

                    // 确定游戏本体路径（优先使用 .dll）
                    String gameBodyPath = gameDll.exists() ?
                            gameDll.getAbsolutePath() : gameExe.getAbsolutePath();

                    Log.d(TAG, "  SMAPI 启动器: " + smapiPath);
                    Log.d(TAG, "  游戏本体: " + gameBodyPath);

                    // 检查 Mods 目录（可能在不同位置）
                    File[] modsDirCandidates = {
                            new File(searchDir, "Mods"),
                            new File(gameDir, "Mods")
                    };

                    for (File modsDir : modsDirCandidates) {
                        if (modsDir.exists() && modsDir.isDirectory()) {
                            Log.d(TAG, "  Mods 目录: " + modsDir.getAbsolutePath());
                            break;
                        }
                    }

                    return new String[] { smapiPath, gameBodyPath };
                }
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "SMAPI 检测失败", e);
            return null;
        }
    }

    /**
     * 查找 SMAPI.Installer.dll 文件
     */
    private static File findSMAPIInstallerDll(File gameDir) {
        File[] candidates = {
                new File(gameDir, "internal/linux/SMAPI.Installer.dll"),
                new File(gameDir, "internal/unix/SMAPI.Installer.dll"),
                new File(gameDir, "SMAPI.Installer.dll")
        };

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 检查 SMAPI 是否已安装
     */
    private static boolean checkSMAPIInstalled(File gameDir) {
        File smapiDll = new File(gameDir, "StardewModdingAPI.dll");
        File smapiInternal = new File(gameDir, "smapi-internal");
        return smapiDll.exists() && smapiInternal.exists() && smapiInternal.isDirectory();
    }

    /**
     * 运行 SMAPI 安装器
     */
    private static void runSMAPIInstaller(android.content.Context context, File installerDll, File gameDir) {
        try {
            Log.i(TAG, "🚀 启动 SMAPI 安装器...");
            Log.i(TAG, "  安装器: " + installerDll.getAbsolutePath());
            Log.i(TAG, "  游戏目录: " + gameDir.getAbsolutePath());

            // 构建参数：--install --game-path "游戏路径" --no-prompt
            String[] args = {
                    "--install",
                    "--game-path", gameDir.getAbsolutePath(),
                    "--no-prompt"
            };

            Log.i(TAG, "  参数: " + String.join(" ", args));

            // 使用 netcorehost API 运行安装器
            // 注意：SMAPI 安装器需要通过 netcorehost 启动，这里暂时跳过
            // 用户需要手动运行安装器或使用其他方式安装 SMAPI
            Log.w(TAG, "[WARN]  SMAPI 安装器需要手动运行或使用其他方式安装");
            Log.w(TAG, "[WARN]  安装器路径: " + installerDll.getAbsolutePath());
            Log.w(TAG, "[WARN]  游戏目录: " + gameDir.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "运行 SMAPI 安装器失败", e);
        }
    }
}