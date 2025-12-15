package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralib.extractors.BasicSevenZipExtractor;
import com.app.ralib.extractors.ExtractorCollection;
import com.app.ralib.extractors.GogShFileExtractor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

        if (availableSpace < requiredSpace) {
            Context context = RaLaunchApplication.getAppContext();
            String errorMsg = context != null ? 
                context.getString(R.string.import_storage_insufficient,
                    requiredSpace / 1024.0 / 1024 / 1024,
                    availableSpace / 1024.0 / 1024 / 1024) :
                String.format("Insufficient storage!\nRequired: %.1f GB\nAvailable: %.1f GB\n\nPlease free up space and try again",
                    requiredSpace / 1024.0 / 1024 / 1024,
                    availableSpace / 1024.0 / 1024 / 1024);
            AppLogger.error(TAG, errorMsg);
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
                        // 本地化来自 BasicSevenZipExtractor 的消息
                        String localizedMessage = localizeExtractionMessage(message);
                        if (extractorIndex == 0) { // GogShFileExtractor
                            listener.onProgress(localizedMessage, (int)(progress*0.7f*100));
                        } else if (extractorIndex == 1) { // BasicSevenZipExtractor
                            listener.onProgress(localizedMessage, (int)((0.7f+progress*0.3f)*100));
                        }
                    }
                }

                @Override
                public void onError(String message, Exception ex, HashMap<String, Object> state) {
                    if (listener != null) {
                        // 本地化错误消息
                        String localizedMessage = localizeExtractionMessage(message);
                        listener.onError(localizedMessage);
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
                                AppLogger.error(TAG, "Game path is null in state");
                                Context context = RaLaunchApplication.getAppContext();
                                String errorMsg = context != null ? 
                                    context.getString(R.string.import_cannot_read_info_failed) :
                                    "Cannot get game path";
                                listener.onError(errorMsg);
                                return;
                            }
                            var modLoaderPath = Paths.get(outputDir, "GoG Games", "ModLoader");
                            listener.onComplete(gamePath.toString(), modLoaderPath.toString());
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
                            Paths.get(""), // 保持压缩包内的原始结构，不跳过任何目录
                            Paths.get(outputDir, "GoG Games", "ModLoader"), // for simplicity
                            extractionListener
                    ))
                    .build()
                    .extractAllInNewThread();
        } catch (Exception e) {
            AppLogger.error(TAG, "Complete installation failed", e);
            if (listener != null) {
                Context context = RaLaunchApplication.getAppContext();
                String errorMsg = context != null ?
                    context.getString(R.string.import_install_failed, e.getMessage()) :
                    "Installation failed: " + e.getMessage();
                listener.onError(errorMsg);
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
                    // 本地化消息
                    String localizedMessage = localizeExtractionMessage(message);
                    if (extractorIndex == 0) { // GogShFileExtractor
                        listener.onProgress(localizedMessage, (int)(progress*100));
                    } else {
                        AppLogger.warn(TAG, "Unknown extractor index: " + extractorIndex);
                    }
                }
            }

            @Override
            public void onError(String message, Exception ex, HashMap<String, Object> state) {
                if (listener != null) {
                    // 本地化错误消息
                    String localizedMessage = localizeExtractionMessage(message);
                    listener.onError(localizedMessage);
                }
            }

            @Override
            public void onComplete(String message, HashMap<String, Object> state) {
                if (listener != null) {
                    var extractorIndex = (int)state.get(ExtractorCollection.STATE_KEY_EXTRACTOR_INDEX); // Im sure it wont be null
                    if (extractorIndex == 0) {
                        var gamePath = (Path)state.get(GogShFileExtractor.STATE_KEY_GAME_PATH);
                        if (gamePath == null) {
                            AppLogger.error(TAG, "Game path is null in state");
                            Context context = RaLaunchApplication.getAppContext();
                            String errorMsg = context != null ?
                                context.getString(R.string.import_cannot_read_info_failed) :
                                "Cannot get game path";
                            listener.onError(errorMsg);
                            return;
                        }
                        listener.onComplete(gamePath.toString(), null);
                    } else {
                        AppLogger.warn(TAG, "Unknown extractor index: " + extractorIndex);
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
     * 本地化解压消息（将 ralib 模块中的硬编码中文消息替换为本地化字符串）
     */
    private static String localizeExtractionMessage(String message) {
        if (message == null) {
            return message;
        }
        
        Context context = RaLaunchApplication.getAppContext();
        if (context == null) {
            return message;
        }
        
        // 匹配硬编码的中文消息并替换为本地化字符串
        if (message.equals("解压完成")) {
            return context.getString(R.string.import_extraction_complete);
        } else if (message.startsWith("正在解压: ")) {
            // 提取文件名
            String fileName = message.substring("正在解压: ".length());
            return context.getString(R.string.import_extracting_file, fileName);
        } else if (message.equals("基础7z解压器解压失败")) {
            return context.getString(R.string.import_extraction_failed);
        }
        
        // 如果消息不匹配，返回原消息
        return message;
    }

    /**
     * 智能分析 ZIP 文件结构,确定正确的解压前缀路径
     *
     * 分析策略:
     * 1. 如果ZIP只有一个根目录,使用该目录作为前缀
     * 2. 如果有多个根目录,检查是否有明显的包装目录(如installer等)
     * 3. 根据文件名推断可能的ModLoader类型
     * 4. 如果无法确定,返回空路径(解压到当前目录)
     *
     * @param zipFilePath ZIP 文件路径
     * @return 适合解压的前缀路径
     */
    public static Path getProperExtractionPrefixForModLoaderZip(Path zipFilePath) {
        try (ZipFile zip = new ZipFile(zipFilePath.toFile())) {
            // 1. 收集所有根级条目(文件和目录)
            List<String> rootEntries = new ArrayList<>();
            List<String> rootDirs = new ArrayList<>();

            zip.stream().forEach(entry -> {
                String name = entry.getName();
                // 提取根级路径(第一个/之前的部分)
                int firstSlash = name.indexOf('/');
                if (firstSlash > 0) {
                    String rootDir = name.substring(0, firstSlash + 1);
                    if (!rootDirs.contains(rootDir)) {
                        rootDirs.add(rootDir);
                    }
                } else if (!name.isEmpty()) {
                    // 根级文件
                    rootEntries.add(name);
                }
            });

            if (rootDirs.size() == 1 && rootEntries.isEmpty()) {
                String singleRoot = rootDirs.get(0);
                return Paths.get(singleRoot);
            }

            if (rootDirs.size() > 1) {
                String selected = selectBestRootDirectory(rootDirs, zipFilePath);
                if (selected != null) {
                    return Paths.get(selected);
                }
            }
            return Paths.get("");

        } catch (IOException e) {
            AppLogger.error(TAG, "ZIP结构分析失败: " + e.getMessage(), e);
            return Paths.get("");
        }
    }

    /**
     * 从多个根目录中选择最佳的解压目录
     *
     * 选择策略:
     * 1. 排除installer/setup/temp等临时/包装目录
     * 2. 优先选择与ZIP文件名相关的目录
     * 3. 选择名称最短的目录(通常是主目录)
     *
     * @param rootDirs 所有根目录列表
     * @param zipFilePath ZIP文件路径(用于推断)
     * @return 最佳目录名,如果无法确定返回null
     */
    private static String selectBestRootDirectory(List<String> rootDirs, Path zipFilePath) {
        if (rootDirs == null || rootDirs.isEmpty()) {
            return null;
        }

        // 需要排除的包装目录关键词(小写)
        List<String> excludeKeywords = Arrays.asList(
                "installer", "setup", "temp", "tmp", "extract",
                "package", "archive", "download"
        );

        // 过滤掉包装目录
        List<String> filtered = rootDirs.stream()
                .filter(dir -> {
                    String lowerDir = dir.toLowerCase();
                    return excludeKeywords.stream()
                            .noneMatch(keyword -> lowerDir.contains(keyword));
                })
                .collect(java.util.stream.Collectors.toList());

        if (filtered.isEmpty()) {
            // 如果全部被过滤,返回原列表中名称最短的
            return rootDirs.stream()
                    .min(java.util.Comparator.comparingInt(String::length))
                    .orElse(null);
        }

        // 获取ZIP文件名(不含扩展名)
        String zipName = zipFilePath.getFileName().toString();
        int dotIndex = zipName.lastIndexOf('.');
        if (dotIndex > 0) {
            zipName = zipName.substring(0, dotIndex);
        }
        final String finalZipName = zipName.toLowerCase();

        // 优先选择与ZIP文件名相关的目录
        String related = filtered.stream()
                .filter(dir -> {
                    String dirName = dir.replace("/", "").toLowerCase();
                    return finalZipName.contains(dirName) || dirName.contains(finalZipName);
                })
                .findFirst()
                .orElse(null);

        if (related != null) {
            return related;
        }

        // 否则选择名称最短的目录(通常是主目录)
        return filtered.stream()
                .min(java.util.Comparator.comparingInt(String::length))
                .orElse(null);
    }



}