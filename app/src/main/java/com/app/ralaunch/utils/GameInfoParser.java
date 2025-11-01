package com.app.ralaunch.utils;

import android.util.Log;

import com.app.ralaunch.RaLaunchApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * 游戏信息解析器
 * 
 * 从游戏安装包中提取游戏信息，支持：
 * - ZIP 压缩包解析
 * - TAR.GZ 压缩包解析
 * - 游戏元数据提取（名称、版本、构建号等）
 * - 自动识别游戏类型
 * 
 * 主要用于导入游戏时自动识别游戏信息
 */
public class GameInfoParser {
    private static final String TAG = "GameInfoParser";
    private static final int HEADER_SIZE = 50000;
    private static final Object extractLock = new Object(); // 同步锁，防止并发提取

    public static class GameInfo {
        public String name;
        public String version;
        public String build;
        public String locale;
        public String timestamp1;
        public String timestamp2;
        public String id;
        public String iconPath;  // 图标文件路径

        @Override
        public String toString() {
            return "GameInfo{" +
                    "name='" + name + '\'' +
                    ", version='" + version + '\'' +
                    ", build='" + build + '\'' +
                    ", locale='" + locale + '\'' +
                    ", iconPath='" + iconPath + '\'' +
                    '}';
        }
    }

    /**
     * 从.sh文件中提取并解析gameinfo
     */
    public static GameInfo extractGameInfo(String shFilePath) {
        try {
            File shFile = new File(shFilePath);
            if (!shFile.exists()) {
                Log.e(TAG, "File not found: " + shFilePath);
                return null;
            }

            // 1. 解析头部获取offset和filesize
            byte[] headerBuffer = new byte[HEADER_SIZE];
            try (FileInputStream fis = new FileInputStream(shFile)) {
                int bytesRead = fis.read(headerBuffer);
                String headerContent = new String(headerBuffer, 0, bytesRead, StandardCharsets.UTF_8);
                
                ExtractionInfo info = parseMakeselfHeader(headerContent);
                if (info == null) {
                    Log.e(TAG, "Failed to parse makeself header");
                    return null;
                }

                Log.d(TAG, "Found offset(lines): " + info.offset + ", filesize: " + info.filesize);

                // 2. 创建临时目录（使用应用程序cache目录）
                File tempDir = new File(RaLaunchApplication.getAppContext().getCacheDir(),
                    "temp_extract_" + Thread.currentThread().getId());
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
                
                // 3. offset是行号，需要找到实际的字节位置
                long byteOffset = 0;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(shFile), StandardCharsets.UTF_8))) {
                    for (long i = 0; i < info.offset; i++) {
                        String line = reader.readLine();
                        if (line == null) break;
                        // 加上行内容长度 + 换行符(1字节)
                        byteOffset += line.getBytes(StandardCharsets.UTF_8).length + 1;
                    }
                }
                
                Log.d(TAG, "Calculated byte offset: " + byteOffset);
                
                // 4. 尝试从mojosetup tar.gz中读取gameinfo
                Log.d(TAG, "First, try reading gameinfo from mojosetup tar.gz at offset: " + byteOffset);
                
                GameInfo gameInfo = null;
                
                try (FileInputStream mojoStream = new FileInputStream(shFile)) {
                    // 跳到mojosetup tar.gz位置
                    long skipped = 0;
                    while (skipped < byteOffset) {
                        long s = mojoStream.skip(byteOffset - skipped);
                        if (s <= 0) break;
                        skipped += s;
                    }
                    
                    Log.d(TAG, "Reading mojosetup tar.gz from position: " + skipped);
                    
                    // 创建限制流读取mojosetup
                    LimitedInputStream mojoLimitedStream = new LimitedInputStream(mojoStream, info.filesize);
                    
                    // 尝试从mojosetup tar.gz读取gameinfo
                    gameInfo = readGameInfoFromTarGz(mojoLimitedStream, tempDir);
                }
                
                // 如果在mojosetup中找到了，直接返回
                if (gameInfo != null) {
                    Log.d(TAG, "Found gameinfo in mojosetup tar.gz");
                    return gameInfo;
                }
                
                // 5. 如果mojosetup中没有，尝试从游戏数据ZIP中读取
                Log.d(TAG, "gameinfo not in mojosetup, trying game data ZIP");
                
                long gameDataOffset = byteOffset + info.filesize;
                Log.d(TAG, "Game data offset: " + gameDataOffset);
                
                // 提取游戏数据ZIP到临时文件（使用唯一文件名）
                File tempZipFile = new File(tempDir, "game_data_" + Thread.currentThread().getId() + ".zip");
                try (FileInputStream gameDataStream = new FileInputStream(shFile);
                     java.io.FileOutputStream tempZipOut = new java.io.FileOutputStream(tempZipFile)) {
                    
                    // 跳到游戏数据ZIP位置
                    long skipped = 0;
                    while (skipped < gameDataOffset) {
                        long s = gameDataStream.skip(gameDataOffset - skipped);
                        if (s <= 0) break;
                        skipped += s;
                    }
                    
                    Log.d(TAG, "Extracting game data ZIP from position: " + skipped);
                    
                    // 复制游戏数据ZIP到临时文件
                    byte[] buffer = new byte[8192];
                    long gameDataSize = shFile.length() - gameDataOffset;
                    long copied = 0;
                    
                    while (copied < gameDataSize) {
                        int toRead = (int) Math.min(buffer.length, gameDataSize - copied);
                        int read = gameDataStream.read(buffer, 0, toRead);
                        if (read <= 0) break;
                        tempZipOut.write(buffer, 0, read);
                        copied += read;
                    }
                    
                    Log.d(TAG, "Extracted " + copied + " bytes to temp ZIP file");
                }
                
                // 直接从临时ZIP文件读取gameinfo（硬编码路径）
                gameInfo = readGameInfoFromZipFile(tempZipFile, tempDir);
                
                // 清理临时ZIP文件
                if (tempZipFile.exists()) {
                    tempZipFile.delete();
                }
                
                return gameInfo;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract game info", e);
            return null;
        }
    }

    /**
     * 限制读取字节数的InputStream包装器
     */
    private static class LimitedInputStream extends java.io.InputStream {
        private final java.io.InputStream source;
        private long remaining;
        
        LimitedInputStream(java.io.InputStream source, long limit) {
            this.source = source;
            this.remaining = limit;
        }
        
        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int result = source.read();
            if (result != -1) remaining--;
            return result;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int result = source.read(b, off, toRead);
            if (result > 0) remaining -= result;
            return result;
        }
    }
    
    /**
     * 从tar.gz流中读取gameinfo和icon.png
     */
    private static GameInfo readGameInfoFromTarGz(java.io.InputStream inputStream, File outputDir) throws IOException {
        File gameinfoFile = null;
        String iconPath = null;
        
        try (GZIPInputStream gzis = new GZIPInputStream(inputStream);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {
            
            Log.d(TAG, "Reading tar.gz format...");
            int entryCount = 0;
            int logCount = 0;
            
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                String entryName = entry.getName();
                entryCount++;
                
                // 只打印前10个条目
                if (logCount < 10) {
                    Log.d(TAG, "  Tar Entry #" + entryCount + ": " + entryName);
                    logCount++;
                }
                
                // 查找gameinfo文件
                if (gameinfoFile == null && entryName.endsWith("gameinfo")) {
                    Log.d(TAG, "*** Found gameinfo in tar.gz at entry #" + entryCount + ": " + entryName);
                    gameinfoFile = extractGameinfoFromTar(tais, entry, outputDir);
                }
                
                // 查找icon文件
                if (iconPath == null && entryName.endsWith("icon.png") && entryName.contains("support")) {
                    Log.d(TAG, "*** Found icon in tar.gz at entry #" + entryCount + ": " + entryName);
                    iconPath = extractIconFromTar(tais, entry, outputDir);
                }
                
                // 如果都找到了就退出
                if (gameinfoFile != null && iconPath != null) {
                    Log.d(TAG, "Found both files in tar.gz, stopping scan at entry #" + entryCount);
                    break;
                }
            }
            
            Log.d(TAG, "Total tar entries scanned: " + entryCount);
            if (gameinfoFile == null) {
                Log.d(TAG, "gameinfo not found in tar.gz");
            }
            
        } catch (IOException e) {
            Log.d(TAG, "Failed to read as tar.gz: " + e.getMessage());
            return null;
        }
        
        // 从提取的gameinfo文件中读取信息
        GameInfo gameInfo = null;
        if (gameinfoFile != null && gameinfoFile.exists()) {
            gameInfo = parseGameInfoFromFile(gameinfoFile);
            if (gameInfo != null) {
                gameInfo.iconPath = iconPath;
            }
        }
        
        return gameInfo;
    }
    
    /**
     * 直接从ZIP文件读取gameinfo和icon.png（使用硬编码路径，避免遍历）
     */
    private static GameInfo readGameInfoFromZipFile(File zipFile, File outputDir) throws IOException {
        File gameinfoFile = null;
        String iconPath = null;
        
        // 硬编码的文件路径
        final String GAMEINFO_PATH = "data/noarch/gameinfo";
        final String ICON_PATH = "data/noarch/support/icon.png";
        
        Log.d(TAG, "Reading ZIP file directly with hardcoded paths");
        
        try (ZipFile zf = new ZipFile(zipFile)) {
            // 直接获取gameinfo条目
            ZipEntry gameinfoEntry = zf.getEntry(GAMEINFO_PATH);
            if (gameinfoEntry != null) {
                Log.d(TAG, "*** Found gameinfo at hardcoded path: " + GAMEINFO_PATH);
                gameinfoFile = new File(outputDir, "gameinfo");
                
                try (java.io.InputStream is = zf.getInputStream(gameinfoEntry);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(gameinfoFile)) {
                    StreamUtils.transferTo(is, fos);
                }
                Log.d(TAG, "Gameinfo extracted to: " + gameinfoFile.getAbsolutePath());
            } else {
                Log.w(TAG, "gameinfo not found at path: " + GAMEINFO_PATH);
            }
            
            // 直接获取icon条目
            ZipEntry iconEntry = zf.getEntry(ICON_PATH);
            if (iconEntry != null) {
                Log.d(TAG, "*** Found icon at hardcoded path: " + ICON_PATH);
                File iconFile = new File(outputDir, "icon.png");
                
                try (java.io.InputStream is = zf.getInputStream(iconEntry);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(iconFile)) {
                    StreamUtils.transferTo(is, fos);
                }
                iconPath = iconFile.getAbsolutePath();
                Log.d(TAG, "Icon extracted to: " + iconPath);
            } else {
                Log.w(TAG, "icon not found at path: " + ICON_PATH);
            }
        }
        
        // 从提取的gameinfo文件中读取信息
        GameInfo gameInfo = null;
        if (gameinfoFile != null && gameinfoFile.exists()) {
            gameInfo = parseGameInfoFromFile(gameinfoFile);
            if (gameInfo != null) {
                gameInfo.iconPath = iconPath;
            }
        }
        
        return gameInfo;
    }
    
    /**
     * 从输入流中读取gameinfo和icon.png（已废弃，太慢）
     * 支持ZIP格式
     */
    @Deprecated
    private static GameInfo readGameInfoFromStream(java.io.InputStream inputStream, File outputDir) throws IOException {
        File gameinfoFile = null;
        String iconPath = null;
        
        // 先尝试ZIP格式
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            Log.d(TAG, "Trying to read as ZIP format...");
            int entryCount = 0;
            int logCount = 0;
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                entryCount++;
                
                // 只打印前10个条目，避免日志过多导致卡顿
                if (logCount < 10) {
                    Log.d(TAG, "  ZIP Entry #" + entryCount + ": " + entryName);
                    logCount++;
                }
                
                // 查找gameinfo文件（可能在data/noarch/下，或者其他位置）
                if (gameinfoFile == null && entryName.endsWith("gameinfo")) {
                    Log.d(TAG, "*** Found gameinfo file in ZIP at entry #" + entryCount + ": " + entryName);
                    gameinfoFile = extractGameinfoFromZip(zis, entry, outputDir);
                }
                
                // 查找icon.png文件
                if (iconPath == null && entryName.endsWith("icon.png") && entryName.contains("support")) {
                    Log.d(TAG, "*** Found icon file in ZIP at entry #" + entryCount + ": " + entryName);
                    iconPath = extractIconFromZip(zis, entry, outputDir);
                }
                
                // 如果都找到了就退出
                if (gameinfoFile != null && iconPath != null) {
                    Log.d(TAG, "Found both files, stopping scan at entry #" + entryCount);
                    break;
                }
                
                zis.closeEntry();
            }
            
            Log.d(TAG, "Total ZIP entries scanned: " + entryCount);
            if (gameinfoFile == null) {
                Log.w(TAG, "gameinfo not found in ZIP after scanning " + entryCount + " entries");
                Log.w(TAG, "This means gameinfo is NOT in the game data ZIP");
                Log.w(TAG, "We need to extract it from a different location in the .sh file");
            } else {
                Log.d(TAG, "Successfully found and extracted gameinfo from ZIP");
            }
        } catch (IOException e) {
            Log.d(TAG, "Not a ZIP format, trying tar.gz: " + e.getMessage());
            // 如果ZIP失败，这个异常是预期的，不需要处理
            // tar.gz的尝试在下面的代码中
        }
        
        // 从提取的gameinfo文件中读取信息
        GameInfo gameInfo = null;
        if (gameinfoFile != null && gameinfoFile.exists()) {
            gameInfo = parseGameInfoFromFile(gameinfoFile);
            if (gameInfo != null) {
                gameInfo.iconPath = iconPath;
            }
        } else {
            Log.w(TAG, "gameinfo file not found in archive");
        }
        
        return gameInfo;
    }
    
    /**
     * 从ZIP流中提取gameinfo文件
     */
    private static File extractGameinfoFromZip(ZipInputStream zis, ZipEntry entry, File outputDir) {
        try {
            File gameinfoFile = new File(outputDir, "gameinfo");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(gameinfoFile)) {
                StreamUtils.transferTo(zis, fos);
            }
            Log.d(TAG, "Gameinfo extracted from ZIP to: " + gameinfoFile.getAbsolutePath());
            return gameinfoFile;
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract gameinfo from ZIP", e);
            return null;
        }
    }
    
    /**
     * 从ZIP流中提取icon文件
     */
    private static String extractIconFromZip(ZipInputStream zis, ZipEntry entry, File outputDir) {
        try {
            File iconFile = new File(outputDir, "icon.png");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(iconFile)) {
                StreamUtils.transferTo(zis, fos);
            }
            Log.d(TAG, "Icon extracted from ZIP to: " + iconFile.getAbsolutePath());
            return iconFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract icon from ZIP", e);
            return null;
        }
    }
    
    /**
     * 从tar流中提取gameinfo文件
     */
    private static File extractGameinfoFromTar(TarArchiveInputStream tais, TarArchiveEntry entry, File outputDir) {
        try {
            File gameinfoFile = new File(outputDir, "gameinfo");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(gameinfoFile)) {
                byte[] buffer = new byte[8192];
                int read;
                long remaining = entry.getSize();
                
                while (remaining > 0 && (read = tais.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }
            Log.d(TAG, "Gameinfo extracted to: " + gameinfoFile.getAbsolutePath());
            return gameinfoFile;
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract gameinfo", e);
            return null;
        }
    }
    
    /**
     * 从tar.gz文件中读取gameinfo和icon.png（已弃用，保留作为备用）
     */
    @Deprecated
    private static GameInfo readGameInfoFromTarGz(File tarGzFile, File outputDir) throws IOException {
        GameInfo gameInfo = null;
        String iconPath = null;
        
        try (FileInputStream fis = new FileInputStream(tarGzFile);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {
            
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                String entryName = entry.getName();
                Log.d(TAG, "Found tar entry: " + entryName);
                
                // 查找data/noarch/gameinfo文件
                if (gameInfo == null && (entryName.contains("data/noarch/gameinfo") || entryName.endsWith("gameinfo"))) {
                    Log.d(TAG, "Found gameinfo file: " + entryName);
                    gameInfo = parseGameInfo(tais);
                }
                
                // 查找data/noarch/support/icon.png文件
                if (iconPath == null && (entryName.contains("data/noarch/support/icon.png") || entryName.endsWith("support/icon.png"))) {
                    Log.d(TAG, "Found icon file: " + entryName);
                    iconPath = extractIconFromTar(tais, entry, outputDir);
                }
                
                // 如果都找到了就退出
                if (gameInfo != null && iconPath != null) {
                    break;
                }
            }
        }
        
        if (gameInfo != null) {
            gameInfo.iconPath = iconPath;
        }
        
        if (gameInfo == null) {
            Log.w(TAG, "gameinfo file not found in tar.gz");
        }
        return gameInfo;
    }
    
    /**
     * 从tar流中提取icon.png
     */
    private static String extractIconFromTar(TarArchiveInputStream tais, TarArchiveEntry entry, File outputDir) {
        try {
            File iconFile = new File(outputDir, "icon.png");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(iconFile)) {
                byte[] buffer = new byte[8192];
                int read;
                long remaining = entry.getSize();
                
                while (remaining > 0 && (read = tais.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }
            Log.d(TAG, "Icon extracted to: " + iconFile.getAbsolutePath());
            return iconFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract icon", e);
            return null;
        }
    }

    /**
     * 从gameinfo文件中解析游戏信息
     */
    private static GameInfo parseGameInfoFromFile(File gameinfoFile) {
        GameInfo info = new GameInfo();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(gameinfoFile), StandardCharsets.UTF_8))) {
            
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;
                
                Log.d(TAG, "Gameinfo line " + lineNum + ": " + line);
                
                switch (lineNum) {
                    case 1:
                        info.name = line;
                        break;
                    case 2:
                        info.version = line;
                        break;
                    case 3:
                        info.build = line;
                        break;
                    case 4:
                        info.locale = line;
                        break;
                    case 5:
                        info.timestamp1 = line;
                        break;
                    case 6:
                        info.timestamp2 = line;
                        break;
                    case 7:
                        info.id = line;
                        break;
                }
            }
            
            Log.d(TAG, "Parsed game info from file: " + info);
            return info;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to read gameinfo file", e);
            return null;
        }
    }
    
    /**
     * 解析gameinfo内容（从流中直接读取，已弃用）
     */
    @Deprecated
    private static GameInfo parseGameInfo(TarArchiveInputStream tais) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(tais, StandardCharsets.UTF_8));
        GameInfo info = new GameInfo();
        
        String line;
        int lineNum = 0;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            line = line.trim();
            if (line.isEmpty()) continue;
            
            Log.d(TAG, "Line " + lineNum + ": " + line);
            
            switch (lineNum) {
                case 1:
                    info.name = line;
                    break;
                case 2:
                    info.version = line;
                    break;
                case 3:
                    info.build = line;
                    break;
                case 4:
                    info.locale = line;
                    break;
                case 5:
                    info.timestamp1 = line;
                    break;
                case 6:
                    info.timestamp2 = line;
                    break;
                case 7:
                    info.id = line;
                    break;
            }
        }
        
        Log.d(TAG, "Parsed game info: " + info);
        return info;
    }

    /**
     * 解析makeself头部
     */
    private static ExtractionInfo parseMakeselfHeader(String content) {
        Log.d(TAG, "Parsing makeself header");
        
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
                        foundFilesize = true;
                    }
                }
            }
            
            if (foundOffset && foundFilesize) break;
        }
        
        if (foundOffset && foundFilesize) {
            return new ExtractionInfo(offset, filesize);
        }
        return null;
    }

    static class ExtractionInfo {
        long offset;
        long filesize;
        
        ExtractionInfo(long offset, long filesize) {
            this.offset = offset;
            this.filesize = filesize;
        }
    }
}

