package com.app.ralaunch.utils;

import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
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

    // 提取信息容器类
    private static class ExtractionInfo {
        long offset = 0;
        long filesize = 0;

        ExtractionInfo(long offset, long filesize) {
            this.offset = offset;
            this.filesize = filesize;
        }
    }

    public interface ExtractionListener {
        void onProgress(String message, int progress);
        void onComplete(String gamePath, String modLoaderPath);
        void onError(String error);
    }

    public static void installCompleteGame(String shFilePath, String modLoaderZipPath,
                                           String outputDir, ExtractionListener listener) {
        new Thread(() -> {
            try {
                // 先读取游戏信息
                GameInfoParser.GameInfo gameInfo = GameInfoParser.extractGameInfo(shFilePath);
                String gameName = (gameInfo != null && gameInfo.name != null) ? gameInfo.name : "Unknown";
                Log.d(TAG, "Installing game: " + gameName);

                // 步骤1: 解压GOG的sh文件
                extractGogGame(shFilePath, outputDir, gameName, new ExtractionListener() {
                    @Override
                    public void onProgress(String message, int progress) {
                        if (listener != null) {
                            listener.onProgress(message, progress / 2);
                        }
                    }

                    @Override
                    public void onComplete(String gamePath, String modLoaderPath) {
                        // 步骤2: 解压ModLoader
                        extractModLoader(modLoaderZipPath, outputDir, gameName, new ExtractionListener() {
                            @Override
                            public void onProgress(String message, int progress) {
                                if (listener != null) {
                                    listener.onProgress(message, 50 + progress / 2);
                                }
                            }

                            @Override
                            public void onComplete(String gamePath, String modPath) {
                                if (listener != null) {
                                    // modPath已经是正确的路径，直接传递
                                    listener.onComplete(gamePath, modPath);
                                }
                            }

                            @Override
                            public void onError(String error) {
                                if (listener != null) {
                                    listener.onError(error);
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (listener != null) {
                            listener.onError(error);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Complete installation failed", e);
                if (listener != null) {
                    listener.onError("安装失败: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 只安装纯游戏（不安装 ModLoader）
     */
    public static void installGameOnly(String shFilePath, String outputDir, ExtractionListener listener) {
        new Thread(() -> {
            try {
                // 读取游戏信息
                GameInfoParser.GameInfo gameInfo = GameInfoParser.extractGameInfo(shFilePath);
                String gameName = (gameInfo != null && gameInfo.name != null) ? gameInfo.name : "Unknown";
                Log.d(TAG, "Installing game only: " + gameName);

                // 只解压 GOG 的 sh 文件
                extractGogGame(shFilePath, outputDir, gameName, new ExtractionListener() {
                    @Override
                    public void onProgress(String message, int progress) {
                        if (listener != null) {
                            listener.onProgress(message, progress);
                        }
                    }

                    @Override
                    public void onComplete(String gamePath, String modLoaderPath) {
                        if (listener != null) {
                            // 纯游戏，没有 modLoaderPath
                            listener.onComplete(gamePath, null);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (listener != null) {
                            listener.onError(error);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Game-only installation failed", e);
                if (listener != null) {
                    listener.onError("安装失败: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 解压GOG的sh游戏文件
     */
    public static void extractGogGame(String inputPath, String outputDir, String gameName, ExtractionListener listener) {
        new Thread(() -> {
            try {
                extractGogGameInternal(inputPath, outputDir, gameName, listener);
            } catch (Exception e) {
                Log.e(TAG, "Extraction failed", e);
                if (listener != null) {
                    listener.onError("解压失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void extractGogGameInternal(String inputPath, String outputDir, String gameName, ExtractionListener listener)
            throws IOException {
        File gameBin = new File(inputPath);
        File outputPath = new File(outputDir);

        if (!outputPath.exists()) {
            outputPath.mkdirs();
        }

        if (!gameBin.exists()) {
            throw new IOException("输入文件不存在: " + inputPath);
        }

        listener.onProgress("正在分析安装文件...", 5);

        // 读取头部来解析信息
        final int HEADER_SIZE = 20480;
        byte[] headerBuffer = new byte[HEADER_SIZE];

        try (FileInputStream fis = new FileInputStream(gameBin)) {
            int bytesRead = fis.read(headerBuffer);
            Log.d(TAG, "Read " + bytesRead + " bytes from header");

            String headerContent = new String(headerBuffer, 0, bytesRead, StandardCharsets.UTF_8);

            // 解析 makeself 头部
            ExtractionInfo extractionInfo = parseMakeselfHeader(headerContent);

            long offset, filesize;

            if (extractionInfo != null) {
                offset = extractionInfo.offset;
                filesize = extractionInfo.filesize;
                Log.d(TAG, "Successfully parsed header - offset: " + offset + ", filesize: " + filesize);
            } else {
                Log.e(TAG, "Failed to parse makeself header, using fallback values");
                offset = 519;
                filesize = 696221;
                Log.d(TAG, "Using fallback values - offset: " + offset + ", filesize: " + filesize);
            }

            if (offset == 0 || filesize == 0) {
                throw new IOException("Invalid parameters after parsing: offset=" + offset + ", filesize=" + filesize);
            }

            Log.d(TAG, "Final extraction parameters - offset: " + offset + ", filesize: " + filesize);

            // 提取文件
            extractData(gameBin, outputPath, offset, filesize, gameName, listener);
            Log.d(TAG, "Game archive unpacking completed successfully");

            // 解压完成后，调用onComplete
            if (listener != null) {
                File gogGamesDir = new File(outputDir, "GoG Games");
                String gamePath = new File(gogGamesDir, gameName).getAbsolutePath();
                listener.onComplete(gamePath, null);
            }

        } catch (Exception e) {
            throw new IOException("Extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 makeself 头部
     */
    private static ExtractionInfo parseMakeselfHeader(String content) {
        Log.d(TAG, "Parsing makeself header, content size: " + content.length());

        String[] lines = content.split("\n");
        long offset = 0;
        long filesize = 0;
        boolean foundOffset = false;
        boolean foundFilesize = false;

        for (String line : lines) {
            if (!foundOffset) {
                if (line.contains("head -n")) {
                    String offsetStr = line.substring(line.indexOf("head -n") + 7);
                    StringBuilder oss = new StringBuilder();
                    for (char c : offsetStr.toCharArray()) {
                        if (Character.isDigit(c)) {
                            oss.append(c);
                        } else if (oss.length() > 0) {
                            break;
                        }
                    }
                    if (oss.length() > 0) {
                        offset = Long.parseLong(oss.toString());
                        Log.d(TAG, "Found offset from 'head -n': " + offset);
                        foundOffset = true;
                    }
                } else if (line.contains("SKIP=")) {
                    String offsetStr = line.substring(line.indexOf("SKIP=") + 5);
                    StringBuilder oss = new StringBuilder();
                    for (char c : offsetStr.toCharArray()) {
                        if (Character.isDigit(c)) {
                            oss.append(c);
                        } else if (oss.length() > 0) {
                            break;
                        }
                    }
                    if (oss.length() > 0) {
                        offset = Long.parseLong(oss.toString());
                        Log.d(TAG, "Found offset from 'SKIP=': " + offset);
                        foundOffset = true;
                    }
                }
            }

            if (!foundFilesize) {
                if (line.contains("filesizes=")) {
                    String sizeStr = line.substring(line.indexOf("filesizes=") + 10);
                    StringBuilder fss = new StringBuilder();
                    for (char c : sizeStr.toCharArray()) {
                        if (Character.isDigit(c)) {
                            fss.append(c);
                        } else if (fss.length() > 0) {
                            break;
                        }
                    }
                    if (fss.length() > 0) {
                        filesize = Long.parseLong(fss.toString());
                        Log.d(TAG, "Found filesize from 'filesizes=': " + filesize);
                        foundFilesize = true;
                    }
                } else if (line.contains("SIZE=")) {
                    String sizeStr = line.substring(line.indexOf("SIZE=") + 5);
                    StringBuilder fss = new StringBuilder();
                    for (char c : sizeStr.toCharArray()) {
                        if (Character.isDigit(c)) {
                            fss.append(c);
                        } else if (fss.length() > 0) {
                            break;
                        }
                    }
                    if (fss.length() > 0) {
                        filesize = Long.parseLong(fss.toString());
                        Log.d(TAG, "Found filesize from 'SIZE=': " + filesize);
                        foundFilesize = true;
                    }
                }
            }

            if (foundOffset && foundFilesize) break;
        }

        Log.d(TAG, "Final parse result - offset: " + offset + ", filesize: " + filesize);

        if (foundOffset && foundFilesize) {
            return new ExtractionInfo(offset, filesize);
        } else {
            return null;
        }
    }

    /**
     * 提取数据
     */
    private static void extractData(File inputFile, File outputDir, long offset, long filesize,
                                    String gameName, ExtractionListener listener) throws IOException {
        Log.d(TAG, "Starting extraction: " + inputFile.getAbsolutePath() + " to " + outputDir.getAbsolutePath());

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        long totalSize = inputFile.length();
        Log.d(TAG, "Total file size: " + totalSize);

        if (offset >= totalSize) {
            throw new IOException("Offset " + offset + " is beyond file size " + totalSize);
        }

        if (filesize == 0 || offset + filesize > totalSize) {
            Log.e(TAG, "Invalid filesize " + filesize + ", adjusting to available size");
            filesize = totalSize - offset;
            Log.d(TAG, "Adjusted filesize: " + filesize);
        }

        try (FileInputStream inputStream = new FileInputStream(inputFile)) {
            // 提取安装脚本
            File unpackerPath = new File(outputDir, "unpacker.sh");
            listener.onProgress("正在提取安装脚本...", 10);

            try (FileOutputStream unpacker = new FileOutputStream(unpackerPath)) {
                inputStream.getChannel().position(0);
                copyStream(inputStream, unpacker, offset, 10, 10, listener);
                Log.d(TAG, "Extracted makeself script to " + unpackerPath.getAbsolutePath());
            }

            // 提取MojoSetup归档
            File mojosetupPath = new File(outputDir, "mojosetup.tar.gz");
            listener.onProgress("正在提取MojoSetup归档...", 20);

            try (FileOutputStream mojosetup = new FileOutputStream(mojosetupPath)) {
                inputStream.getChannel().position(offset);
                copyStream(inputStream, mojosetup, filesize, 20, 20, listener);
                Log.d(TAG, "Extracted MojoSetup archive to " + mojosetupPath.getAbsolutePath() + ", size: " + filesize);
            }

            // 提取游戏数据
            long gameDataStart = offset + filesize;
            long gameDataSize = totalSize - gameDataStart;

            if (gameDataSize > 0) {
                File gameDataPath = new File(outputDir, "data_temp.zip");
                listener.onProgress("正在提取游戏数据...", 40);

                try (FileOutputStream gameData = new FileOutputStream(gameDataPath)) {
                    inputStream.getChannel().position(gameDataStart);
                    copyStream(inputStream, gameData, gameDataSize, 40, 30, listener);
                    Log.d(TAG, "Extracted game files to " + gameDataPath.getAbsolutePath() + ", size: " + gameDataSize);
                }

                // 使用SevenZipJBinding解压data/noarch/game到目标目录
                listener.onProgress("正在解压游戏文件...", 70);
                File gameFilesDir = new File(outputDir, "GoG Games/" + gameName);

                if (extractGameDataWithSevenZip(gameDataPath, gameFilesDir, 70, 20, listener)) {
                    Log.d(TAG, "Successfully extracted game files to: " + gameFilesDir.getAbsolutePath());

                    // 清理临时文件
                    Log.d(TAG, "Cleaning up temporary extraction files...");
                    cleanupExtractionTempFiles(outputDir.getAbsolutePath());
                } else {
                    throw new IOException("Failed to extract game files from data_temp.zip");
                }
            } else {
                Log.d(TAG, "No game data found after MojoSetup archive");
            }
        }

        listener.onProgress("游戏数据提取完成", 90);
    }

    /**
     * 使用SevenZipJBinding解压data_temp.zip中的游戏文件
     */
    private static boolean extractGameDataWithSevenZip(File archiveFile, File targetDir,
                                                       int startProgress, int progressRange,
                                                       ExtractionListener listener) {
        if (listener != null) {
            listener.onProgress("正在初始化解压引擎...", startProgress);
        }

        try {
            // 确保目标目录存在
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            try (RandomAccessFile randomAccessFile = new RandomAccessFile(archiveFile, "r");
                 RandomAccessFileInStream inStream = new RandomAccessFileInStream(randomAccessFile);
                 IInArchive inArchive = SevenZip.openInArchive(null, inStream)) {

                int totalItems = inArchive.getNumberOfItems();
                Log.d(TAG, "Archive contains " + totalItems + " items");

                // 创建自定义回调
                GameDataExtractCallback callback = new GameDataExtractCallback(
                        inArchive, targetDir, "data/noarch/game/",
                        listener, startProgress, progressRange, totalItems);

                // 提取所有文件
                inArchive.extract(null, false, callback);

                Log.d(TAG, "SevenZip extraction completed successfully");
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "SevenZip extraction failed", e);
            // 如果SevenZip解压失败，回退到原来的ZipFile方法
            Log.d(TAG, "Falling back to standard ZipFile extraction");
            return extractGameDataDirect(archiveFile, targetDir, startProgress, progressRange, listener);
        }
    }

    /**
     * SevenZipJBinding 解压回调类
     */
    private static class GameDataExtractCallback implements IArchiveExtractCallback {
        private final IInArchive inArchive;
        private final File targetDir;
        private final String targetPrefix;
        private final ExtractionListener listener;
        private final int startProgress;
        private final int progressRange;
        private final int totalItems;
        private int processedItems = 0;

        public GameDataExtractCallback(IInArchive inArchive, File targetDir, String targetPrefix,
                                       ExtractionListener listener, int startProgress,
                                       int progressRange, int totalItems) {
            this.inArchive = inArchive;
            this.targetDir = targetDir;
            this.targetPrefix = targetPrefix;
            this.listener = listener;
            this.startProgress = startProgress;
            this.progressRange = progressRange;
            this.totalItems = totalItems;
        }

        @Override
        public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
            try {
                // 获取文件信息
                String filePath = inArchive.getStringProperty(index, PropID.PATH);
                boolean isFolder = (Boolean) inArchive.getProperty(index, PropID.IS_FOLDER);

                Log.d(TAG, "Processing item: " + filePath + " (isFolder: " + isFolder + ")");

                // 只处理目标前缀下的文件，且不是文件夹
                if (filePath.startsWith(targetPrefix) && !isFolder) {
                    // 移除前缀
                    String relativePath = filePath.substring(targetPrefix.length());
                    File targetFile = new File(targetDir, relativePath);

                    // 安全检查：确保目标文件在目标目录内
                    String canonicalDestPath = targetDir.getCanonicalPath();
                    String canonicalEntryPath = targetFile.getCanonicalPath();

                    if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                        throw new SevenZipException("ZIP条目在目标目录之外: " + filePath);
                    }

                    // 创建父目录
                    File parent = targetFile.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }

                    // 返回输出流
                    return new SequentialOutStream(targetFile);
                }

                // 不需要解压的文件返回null
                return null;

            } catch (Exception e) {
                throw new SevenZipException("Error getting stream for index " + index, e);
            }
        }

        @Override
        public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
            // 准备操作，可以在这里进行一些初始化
        }

        @Override
        public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
            processedItems++;

            // 更新进度
            if (listener != null && totalItems > 0) {
                int progress = startProgress + (int) ((processedItems * progressRange) / totalItems);
                listener.onProgress("正在解压游戏文件...", progress);
            }

            if (extractOperationResult != ExtractOperationResult.OK) {
                Log.w(TAG, "Extraction operation result: " + extractOperationResult);
            }
        }

        @Override
        public void setTotal(long total) throws SevenZipException {
            Log.d(TAG, "Total bytes to extract: " + total);
        }

        @Override
        public void setCompleted(long complete) throws SevenZipException {
            // 可以在这里更新基于字节的进度
        }
    }

    /**
     * SevenZipJBinding 输出流实现
     */
    private static class SequentialOutStream implements ISequentialOutStream {
        private final FileOutputStream outputStream;

        public SequentialOutStream(File targetFile) throws FileNotFoundException {
            this.outputStream = new FileOutputStream(targetFile);
        }

        @Override
        public int write(byte[] data) throws SevenZipException {
            try {
                outputStream.write(data);
                return data.length;
            } catch (IOException e) {
                throw new SevenZipException("Error writing to output stream", e);
            }
        }
    }

    /**
     * 保留原来的ZipFile方法作为fallback
     */
    private static boolean extractGameDataDirect(File zipFile, File targetDir, int startProgress, int progressRange,
                                                 ExtractionListener listener) {
        if (listener != null) {
            listener.onProgress("正在解压游戏文件...", startProgress);
        }

        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();

            // 先统计需要解压的文件数量
            int totalFiles = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith("data/noarch/game/") && !entry.isDirectory()) {
                    totalFiles++;
                }
            }

            // 重新开始解压
            entries = zip.entries();
            int processedFiles = 0;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // 只处理data/noarch/game/目录下的文件
                if (entryName.startsWith("data/noarch/game/") && !entry.isDirectory()) {
                    // 移除data/noarch/game/前缀
                    String relativePath = entryName.substring("data/noarch/game/".length());
                    File targetFile = new File(targetDir, relativePath);

                    // 防止ZIP滑动攻击
                    String canonicalDestPath = targetDir.getCanonicalPath();
                    String canonicalEntryPath = targetFile.getCanonicalPath();

                    if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                        throw new IOException("ZIP条目在目标目录之外: " + entryName);
                    }

                    // 创建父目录
                    File parent = targetFile.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }

                    // 解压文件
                    try (InputStream is = zip.getInputStream(entry)) {
                        java.nio.file.Files.copy(is, targetFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }

                    processedFiles++;
                    if (listener != null && totalFiles > 0) {
                        int progress = startProgress + (int)((processedFiles * progressRange) / totalFiles);
                        listener.onProgress("正在解压: " + targetFile.getName(), progress);
                    }
                }
            }


            if (listener != null) {
                listener.onProgress("游戏文件解压完成", startProgress + progressRange);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Direct game data extraction failed", e);
            return false;
        }
    }

    /**
     * 复制流数据
     */
    private static void copyStream(InputStream input, OutputStream output, long totalBytes,
                                   int startProgress, int progressRange, ExtractionListener listener) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;

        while (copied < totalBytes) {
            int toRead = (int) Math.min(buffer.length, totalBytes - copied);
            int bytesRead = input.read(buffer, 0, toRead);
            if (bytesRead == -1) break;

            output.write(buffer, 0, bytesRead);
            copied += bytesRead;

            if (listener != null) {
                int progress = startProgress + (int) ((copied * progressRange) / totalBytes);
                listener.onProgress("正在提取数据...", progress);
            }
        }
    }

    public static void extractModLoader(String zipPath, String outputDir, String gameName, ExtractionListener listener) {
        new Thread(() -> {
            try {
                File modLoaderZip = new File(zipPath);
                if (!modLoaderZip.exists()) {
                    throw new IOException("ModLoader文件不存在: " + zipPath);
                }

                // 根据压缩包名称创建目录（去掉.zip扩展名）
                String zipFileName = modLoaderZip.getName();
                String modLoaderDirName = zipFileName.endsWith(".zip") 
                    ? zipFileName.substring(0, zipFileName.length() - 4) 
                    : zipFileName;
                
                Log.d(TAG, "ModLoader directory name from zip: " + modLoaderDirName);

                File gogGamesDir = new File(outputDir, "GoG Games");
                File modLoaderDir = new File(gogGamesDir, modLoaderDirName);

                if (!modLoaderDir.exists()) {
                    modLoaderDir.mkdirs();
                }

                // 先复制ModLoader.zip到目标目录
                File targetZipFile = new File(outputDir, "modloader.zip");
                listener.onProgress("正在复制ModLoader文件...", 10);

                if (copyFile(modLoaderZip, targetZipFile)) {
                    Log.d(TAG, "Successfully copied modloader.zip to: " + targetZipFile.getAbsolutePath());
                    listener.onProgress("ModLoader文件复制完成", 20);
                } else {
                    Log.w(TAG, "Failed to copy modloader.zip, but will continue with extraction");
                }

                // 直接解压到目标目录
                if (extractModLoaderZipDirect(modLoaderZip, modLoaderDir, 20, 80, listener)) {
                    listener.onProgress("ModLoader安装完成", 100);

                    if (listener != null) {
                        String gamePath = new File(gogGamesDir, gameName).getAbsolutePath();
                        String modPath = modLoaderDir.getAbsolutePath();
                        listener.onComplete(gamePath, modPath);
                    }
                } else {
                    throw new IOException("Failed to extract ModLoader");
                }

            } catch (Exception e) {
                Log.e(TAG, "ModLoader extraction failed", e);
                if (listener != null) {
                    listener.onError("ModLoader解压失败: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 复制文件
     */
    private static boolean copyFile(File sourceFile, File targetFile) {
        try {
            java.nio.file.Files.copy(sourceFile.toPath(), targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file: " + sourceFile.getAbsolutePath() + " to " + targetFile.getAbsolutePath(), e);
            return false;
        }
    }

    private static boolean extractModLoaderZipDirect(File zipFile, File targetDir, int startProgress, int progressRange,
                                                      ExtractionListener listener) {
        if (listener != null) {
            listener.onProgress("正在解压ModLoader...", startProgress);
        }

        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();

            int totalEntries = zip.size();
            int processedEntries = 0;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // 跳过根目录条目，直接解压内容到目标目录
                if (entryName.equals("ModLoader/") || entryName.equals("ModLoader") ||
                    entryName.equals("tModLoader/") || entryName.equals("tModLoader")) {
                    continue;
                }

                // 移除ModLoader/或tModLoader/前缀（如果存在）
                String relativePath = entryName;
                if (entryName.startsWith("ModLoader/")) {
                    relativePath = entryName.substring("ModLoader/".length());
                } else if (entryName.startsWith("tModLoader/")) {
                    relativePath = entryName.substring("tModLoader/".length());
                }

                File targetFile = new File(targetDir, relativePath);

                // 防止ZIP滑动攻击
                String canonicalDestPath = targetDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();

                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    throw new IOException("ZIP条目在目标目录之外: " + entryName);
                }

                if (entry.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                    continue;
                }

                // 创建父目录
                File parent = targetFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }

                // 解压文件
                try (InputStream is = zip.getInputStream(entry)) {
                    java.nio.file.Files.copy(is, targetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

                processedEntries++;
                if (listener != null && totalEntries > 0) {
                    int progress = startProgress + (int)((processedEntries * progressRange) / totalEntries);
                    listener.onProgress("正在解压ModLoader文件: " + entry.getName(), progress);
                }
            }


            if (listener != null) {
                listener.onProgress("ModLoader解压完成", startProgress + progressRange);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "ModLoader ZIP extraction failed", e);
            return false;
        }
    }

    /**
     * 清理临时文件
     */
    private static void cleanupExtractionTempFiles(String outputDir) {
        Log.d(TAG, "Starting cleanup of extraction temporary files in: " + outputDir);

        String[] filesToCleanup = {
                "unpacker.sh",
                "mojosetup.tar.gz",
                "data_temp.zip",
                "modloader.zip"
        };

        for (String filename : filesToCleanup) {
            File file = new File(outputDir, filename);
            try {
                if (java.nio.file.Files.deleteIfExists(file.toPath())) {
                    Log.d(TAG, "Successfully removed: " + file.getAbsolutePath());
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to cleanup: " + file.getAbsolutePath(), e);
            }
        }

        Log.d(TAG, "Extraction temporary files cleanup completed");
    }
}