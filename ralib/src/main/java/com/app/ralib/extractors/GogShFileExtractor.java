package com.app.ralib.extractors;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.app.ralib.utils.TemporaryFileAcquirer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class GogShFileExtractor implements ExtractorCollection.IExtractor {
    private static final String TAG = "GogShFileExtractor";

    private static final String EXTRACTED_MOJOSETUP_TAR_GZ_FILENAME = "mojosetup.tar.gz";
    private static final String EXTRACTED_GAME_DATA_ZIP_FILENAME = "game_data.zip";

    // Type: Path
    public static final String STATE_KEY_GAME_PATH = GogShFileExtractor.class.getSimpleName()+".game_path";
    // Type: GameDataZipFile
    public static final String STATE_KEY_GAME_DATA_ZIP_FILE = GogShFileExtractor.class.getSimpleName()+".game_data_zip_file";

    public static class MakeSelfShFile {
        long offset = 0; // file offset in bytes where mojosetup.tar.gz starts
        long filesize = 0; // size in bytes of mojosetup.tar.gz

        @Nullable
        public static MakeSelfShFile parse(Path filePath) {
            // 读取头部来解析信息
            // 限制最多读取20480字节
            final int HEADER_SIZE = 20480;
            byte[] headerBuffer = new byte[HEADER_SIZE];
            String headerContent;

            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                int bytesRead = fis.read(headerBuffer);
                Log.d(TAG, "Read " + bytesRead + " bytes from header");
                headerContent = new String(headerBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            }
            catch (Exception ex) {
                Log.e(TAG, "Error when reading MakeSelf SH file", ex);
                return null;
            }

            return parseMakeSelfShFileContent(headerContent);
        }

        /**
         * 解析 makeself 头部
         */
        private static MakeSelfShFile parseMakeSelfShFileContent(String content) {
            Log.d(TAG, "Parsing makeself file content, content size: " + content.length());

            String[] lines = content.split("\n");
            long lineOffset = 0;
            long filesize = 0;
            boolean foundLineOffset = false;
            boolean foundFilesize = false;

            for (String line : lines) {
                if (!foundLineOffset) {
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
                            lineOffset = Long.parseLong(oss.toString());
                            Log.d(TAG, "Found lineOffset from 'head -n': " + lineOffset);
                            foundLineOffset = true;
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
                            lineOffset = Long.parseLong(oss.toString());
                            Log.d(TAG, "Found lineOffset from 'SKIP=': " + lineOffset);
                            foundLineOffset = true;
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

                if (foundLineOffset && foundFilesize) break;
            }

            Log.d(TAG, "Final parse result - lineOffset: " + lineOffset + ", filesize: " + filesize);

            if (foundLineOffset && foundFilesize) {
                if (lineOffset > lines.length) {
                    Log.e(TAG, "Parsed lineOffset is greater than number of lines, invalid makeself file");
                    return null;
                }
                var offset = Arrays.stream(lines)
                        .limit(lineOffset)
                        .map(x -> x.length() + 1)
                        .reduce(0, Integer::sum);

                MakeSelfShFile shFile = new MakeSelfShFile();
                shFile.offset = offset;
                shFile.filesize = filesize;
                return shFile;
            } else {
                return null;
            }
        }
    }

    public static class GameDataZipFile {
        public static final String CONFIG_LUA_PATH = "scripts/config.lua";
        public static final String GAMEINFO_PATH = "data/noarch/gameinfo";
        public static final String ICON_PATH = "data/noarch/support/icon.png";

        @Nullable
        public String id;
        @Nullable
        public String version;
        @Nullable
        public String build;
        @Nullable
        public String locale;
        @Nullable
        public String timestamp1;
        @Nullable
        public String timestamp2;
        @Nullable
        public String gogId;

        private static String getFileContent(ZipFile zip, String entryPath) {
            var entry = zip.getEntry(entryPath);
            if (entry == null) {
                Log.w(TAG, "未在压缩包中找到 " + entryPath);
                return null;
            }
            try (var stream = zip.getInputStream(entry)) {
                Log.d(TAG, "Reading entry " + entryPath + "...");
                return getFileContentFromStream(stream);
            }
            catch (IOException e) {
                Log.w(TAG, "IOException when reading " + entryPath, e);
                return null;
            }
        }

        private static String getFileContentFromStream(InputStream is) throws IOException {
            final int MAX_SIZE = 20480;
            byte[] contentBuffer = new byte[MAX_SIZE];
            int bytesRead = is.read(contentBuffer);
            Log.d(TAG, "Read " + bytesRead + " bytes!");
            return new String(contentBuffer, 0, bytesRead, StandardCharsets.UTF_8);
        }

        public static GameDataZipFile parseFromGogShFile(Path filePath) {
            var shFile = MakeSelfShFile.parse(filePath);
            if (shFile == null) {
                Log.e(TAG, "MakeSelf SH file is null");
                return null;
            }

            try (var tfa = new TemporaryFileAcquirer()) {
                Path tempZipFile = tfa.acquireTempFilePath("temp_game_data.zip");

                // Extract game_data.zip portion to temp file using RandomAccessFile
                try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r");
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(tempZipFile.toFile())) {

                    long gameDataStart = shFile.offset + shFile.filesize;
                    raf.seek(gameDataStart);

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = raf.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }

                // Use the existing parse method which uses ZipFile (much faster than ZipInputStream)
                return GameDataZipFile.parse(tempZipFile);

            } catch (FileNotFoundException ex) {
                Log.e(TAG, "File not found: " + filePath, ex);
                return null;
            } catch (IOException ex) {
                Log.e(TAG, "IOException when reading GOG SH file: " + filePath, ex);
                return null;
            }
        }

        @Nullable
        public static GameDataZipFile parse(Path filePath) {
            try (var zip = new ZipFile(filePath.toFile())) {
                GameDataZipFile gameDataZipFile = new GameDataZipFile();

                var gameInfoContent = getFileContent(zip, GAMEINFO_PATH);
                if (gameInfoContent != null) {
                    if (parseGameInfoContent(gameDataZipFile, gameInfoContent)) {
                        return gameDataZipFile;
                    }
                    Log.w(TAG, "Failed to parse gameinfo content, trying config.lua...");
                }

                var configLuaContent = getFileContent(zip, CONFIG_LUA_PATH);
                if (configLuaContent != null) {
                    if (parseConfigLuaContent(gameDataZipFile, configLuaContent)) {
                        return gameDataZipFile;
                    }
                    Log.w(TAG, "Failed to parse config.lua content");
                }

                Log.e(TAG, "Failed to parse game_data.zip content for id");
                return null;
            } catch (ZipException e) {
                Log.e(TAG, "ZipException when reading game_data.zip", e);
                return null;
            } catch (IOException e) {
                Log.e(TAG, "IOException when reading game_data.zip", e);
                return null;
            }
        }

        private static boolean parseConfigLuaContent(GameDataZipFile gameDataZipFile, String content) {
            String[] lines = content.split("\n");

            for (String line : lines) {
                if (line.contains("id = ")) {
                    StringBuilder idBuilder = new StringBuilder();
                    boolean inQuotes = false;
                    for (char c : line.toCharArray()) {
                        if (c == '"' || c == '\'') {
                            if (inQuotes) {
                                break;
                            } else {
                                inQuotes = true;
                            }
                        } else if (inQuotes) {
                            idBuilder.append(c);
                        }
                    }
                    if (idBuilder.length() > 0) {
                        gameDataZipFile.id = idBuilder.toString();
                        Log.d(TAG, "Found id from config.lua: " + gameDataZipFile.id);
                        return true;
                    }
                }
            }
            Log.w(TAG, "cannot extract id from " + CONFIG_LUA_PATH);
            return false;
        }

        private static boolean parseGameInfoContent(GameDataZipFile gameDataZipFile, String content) {
            String[] lines = content.split("\n");
            if (lines.length < 1) {
                Log.w(TAG, "cannot even extract id from " + GAMEINFO_PATH);
                return false;
            }

            // Parse fields in order: id, version, build, locale, timestamp1, timestamp2, gogId
            gameDataZipFile.id = lines.length > 0 ? lines[0].trim() : null;
            gameDataZipFile.version = lines.length > 1 ? lines[1].trim() : null;
            gameDataZipFile.build = lines.length > 2 ? lines[2].trim() : null;
            gameDataZipFile.locale = lines.length > 3 ? lines[3].trim() : null;
            gameDataZipFile.timestamp1 = lines.length > 4 ? lines[4].trim() : null;
            gameDataZipFile.timestamp2 = lines.length > 5 ? lines[5].trim() : null;
            gameDataZipFile.gogId = lines.length > 6 ? lines[6].trim() : null;

            Log.d(TAG, "Parsed gameinfo - id: " + gameDataZipFile.id + ", version: " + gameDataZipFile.version
                    + ", build: " + gameDataZipFile.build + ", locale: " + gameDataZipFile.locale
                    + ", timestamp1: " + gameDataZipFile.timestamp1 + ", timestamp2: " + gameDataZipFile.timestamp2
                    + ", gogId: " + gameDataZipFile.gogId);

            // At minimum, we need the id
            return gameDataZipFile.id != null && !gameDataZipFile.id.isEmpty();
        }

        @NonNull
        @Override
        public String toString() {
            return "GameDataZipFile{" +
                    "id='" + id + '\'' +
                    ", version='" + version + '\'' +
                    ", build='" + build + '\'' +
                    ", locale='" + locale + '\'' +
                    ", timestamp1='" + timestamp1 + '\'' +
                    ", timestamp2='" + timestamp2 + '\'' +
                    ", gogId='" + gogId + '\'' +
                    '}';
        }
    }

    // backing fields for setters
    private Path sourcePath;
    private Path destinationPath;
    @Nullable
    private ExtractorCollection.ExtractionListener extractionListener;
    private HashMap<String, Object> state;

    public GogShFileExtractor(Path sourcePath, Path destinationPath, ExtractorCollection.ExtractionListener listener) {
        this.setSourcePath(sourcePath);
        this.setDestinationPath(destinationPath);
        this.setExtractionListener(listener);
    }

    @Override
    public void setSourcePath(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public void setDestinationPath(Path destinationPath) {
        this.destinationPath = destinationPath;
    }

    @Override
    public void setExtractionListener(@Nullable ExtractorCollection.ExtractionListener listener) {
        this.extractionListener = listener;
    }

    @Override
    public void setState(HashMap<String, Object> state) {
        this.state = state;
    }

    @Override
    public HashMap<String, Object> getState() {
        return state;
    }

    @Override
    public boolean extract() {
        try (var tfa = new TemporaryFileAcquirer()) {
            // 获取 MakeSelf SH 文件的头部信息
            extractionListener.onProgress("正在提取安装脚本...", 0.01f, state);
            MakeSelfShFile shFile = MakeSelfShFile.parse(this.sourcePath);
            if (shFile == null) {
                Log.e(TAG, "MakeSelf SH file is null");
                throw new IOException("解析 MakeSelf Sh 文件头部失败");
            }
            Log.d(TAG, "Successfully parsed header - offset: " + shFile.offset + ", filesize: " + shFile.filesize);

            try (var fis = new FileInputStream(sourcePath.toFile())){
                Log.d(TAG, "Starting extraction: " + sourcePath + " to " + destinationPath);
                /*
                 * 文件结构: |-- MakeSelf SH Header --|-- mojosetup.tar.gz --|-- game_data.zip --|
                 */

                Files.createDirectories(destinationPath);
                var srcChannel = fis.getChannel();

                // sanity check
                if (shFile.offset + shFile.filesize > srcChannel.size()) {
                    Log.e(TAG, "Invalid offset/filesize: offset=" + shFile.offset + ", filesize=" + shFile.filesize + ", totalSize=" + srcChannel.size());
                    throw new IOException("MakeSelf Sh 文件头部信息无效，超出文件总大小");
                }

                extractionListener.onProgress("正在提取MojoSetup归档...", 0.02f, state);

                // 提取 mojosetup.tar.gz
                Path mojosetupPath = tfa.acquireTempFilePath(EXTRACTED_MOJOSETUP_TAR_GZ_FILENAME);
                try (var mojosetupFos = new java.io.FileOutputStream(mojosetupPath.toFile())) {
                    var mojosetupChannel = mojosetupFos.getChannel();
                    Log.d(TAG, "Extracting mojosetup.tar.gz to " + mojosetupPath);
                    srcChannel.transferTo(shFile.offset, shFile.filesize, mojosetupChannel);
                }

                extractionListener.onProgress("正在提取游戏数据...", 0.03f, state);

                // 提取 game_data.zip
                Path gameDataPath = tfa.acquireTempFilePath(EXTRACTED_GAME_DATA_ZIP_FILENAME);
                try (var gameDataFos = new java.io.FileOutputStream(gameDataPath.toFile())) {
                    var gameDataChannel = gameDataFos.getChannel();
                    Log.d(TAG, "Extracting game_data.zip to " + gameDataPath);
                    srcChannel.transferTo(
                            shFile.offset + shFile.filesize,
                            srcChannel.size() - (shFile.offset + shFile.filesize),
                            gameDataChannel);
                }

                extractionListener.onProgress("正在解析游戏数据...", 0.09f, state);

                Log.d(TAG, "Extraction from MakeSelf SH file completed successfully");

                // 解压 game_data.zip
                Log.d(TAG, "Trying to extract game_data.zip...");
                GameDataZipFile gdzf = GameDataZipFile.parse(gameDataPath);
                if (gdzf == null) {
                    Log.e(TAG, "Failed to parse game_data.zip");
                    throw new IOException("解析 game_data.zip 失败");
                }

                extractionListener.onProgress("正在解压游戏数据...", 0.1f, state);

                var gamePath = destinationPath.resolve(Paths.get("GoG Games", gdzf.id));
                var zipExtractor = new BasicSevenZipExtractor(
                        gameDataPath,
                        Paths.get("data/noarch/game"),
                        gamePath,
                        new ExtractorCollection.ExtractionListener() {
                            @Override
                            public void onProgress(String message, float progress, HashMap<String, Object> state) {
                                extractionListener.onProgress(message, 0.1f + progress * 0.9f, state);
                            }

                            @Override
                            public void onComplete(String message, HashMap<String, Object> state) {}

                            @Override
                            public void onError(String message, Exception ex, HashMap<String, Object> state) {
                                throw new RuntimeException(message, ex);
                            }
                        });
                zipExtractor.setState(state);
                var isGameDataExtracted = zipExtractor.extract();
                if (!isGameDataExtracted) {
                    Log.e(TAG, "Failed to extract game_data.zip");
                    throw new IOException("解压 game_data.zip 失败");
                }

                extractionListener.onProgress("游戏数据提取完成", 1.0f, state);
                state.put(STATE_KEY_GAME_PATH, gamePath);
                state.put(STATE_KEY_GAME_DATA_ZIP_FILE, gdzf);
                extractionListener.onComplete("游戏数据提取完成", state);

                return true;
            }
        }
        catch (Exception ex) {
            Log.e(TAG, "Error when extracting source file", ex);
            extractionListener.onError("GoG Sh 文件解压失败", ex, state);
            return false;
        }
    }
}
