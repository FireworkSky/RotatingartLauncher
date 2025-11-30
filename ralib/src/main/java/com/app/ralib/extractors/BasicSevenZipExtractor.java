package com.app.ralib.extractors;

import android.util.Log;

import androidx.annotation.Nullable;

import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class BasicSevenZipExtractor implements ExtractorCollection.IExtractor {
    private static final String TAG = "BasicSevenZipExtractor";
    private Path sourcePath;
    private Path sourceExtractionPrefix;
    private Path destinationPath;
    private ExtractorCollection.ExtractionListener extractionListener;
    private HashMap<String, Object> state;

    public BasicSevenZipExtractor(Path sourcePath, Path destinationPath) {
        this.setSourcePath(sourcePath);
        this.setDestinationPath(destinationPath);
        this.setSourceExtractionPrefix(Paths.get(""));
    }

    public BasicSevenZipExtractor(Path sourcePath, Path destinationPath, ExtractorCollection.ExtractionListener listener) {
        this.setSourcePath(sourcePath);
        this.setDestinationPath(destinationPath);
        this.setExtractionListener(listener);
        this.setSourceExtractionPrefix(Paths.get(""));
    }

    public BasicSevenZipExtractor(Path sourcePath, Path sourceExtractionPrefix, Path destinationPath, ExtractorCollection.ExtractionListener listener) {
        this.setSourcePath(sourcePath);
        this.setDestinationPath(destinationPath);
        this.setExtractionListener(listener);
        this.setSourceExtractionPrefix(sourceExtractionPrefix);
    }

    @Override
    public void setSourcePath(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public void setSourceExtractionPrefix(Path sourceExtractionPrefix) {
        this.sourceExtractionPrefix = sourceExtractionPrefix;
    }

    @Override
    public void setDestinationPath(Path destinationPath) {
        this.destinationPath = destinationPath;
    }

    @Override
    public void setExtractionListener(ExtractorCollection.ExtractionListener listener) {
        this.extractionListener = listener;
    }

    @Override
    public void setState(HashMap<String, Object> state) {
        this.state = state;
    }

    @Override
    public HashMap<String, Object> getState() {
        return this.state;
    }

    @Override
    public boolean extract() {
        try {
            if (!Files.exists(destinationPath)) {
                Files.createDirectories(destinationPath);
            }

            try (RandomAccessFile raf = new RandomAccessFile(sourcePath.toString(), "r");
                 RandomAccessFileInStream is = new RandomAccessFileInStream(raf);
                 IInArchive archive = SevenZip.openInArchive(null, is)) {

                int totalItems = archive.getNumberOfItems();
                Log.d(TAG, "Archive contains " + totalItems + " items");

                // 提取所有文件
                archive.extract(null, false, new ArchiveExtractCallback(archive));
            }

            Log.d(TAG, "SevenZip extraction completed successfully");
            if (extractionListener != null) {
                extractionListener.onProgress("解压完成", 1.0f, state);
                extractionListener.onComplete("解压完成", state);
            }
            return true;
        }
        catch (Exception ex) {
            if (extractionListener != null) {
                extractionListener.onError("基础7z解压器解压失败", ex, state);
            }
        }
        return false;
    }

    /*
     * SevenZipJBinding 提取回调实现
     */
    private class ArchiveExtractCallback implements IArchiveExtractCallback {
        private final IInArchive archive;
        @Nullable
        private SequentialFileOutputStream outputStream = null;
        @Nullable
        private Path currentProcessingFilePath = null;
        private long totalBytes = 0;
        private long totalBytesExtracted = 0;

        public ArchiveExtractCallback(IInArchive archive) {
            this.archive = archive;
        }


        @Override
        public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
            try {
                closeOutputStream();

                // 获取文件信息
                Path filePath = Paths.get(archive.getStringProperty(index, PropID.PATH));
                boolean isFolder = (Boolean) archive.getProperty(index, PropID.IS_FOLDER);

             //   Log.d(TAG, "Processing item: " + filePath + " (isFolder: " + isFolder + ")");

                // 跳过非指定前缀的文件
                Path relativeFilePath = sourceExtractionPrefix.relativize(filePath).normalize();
                if (relativeFilePath.toString().startsWith("..")) {
                    return null;
                }

                // 计算目标文件路径并防止路径遍历攻击
                Path targetFilePath = destinationPath.resolve(relativeFilePath).normalize();
                if (destinationPath.relativize(targetFilePath).toString().startsWith("..")) {
                    throw new SevenZipException("Attempting to write outside of destination directory: " + targetFilePath);
                }

                // 对于文件夹只创建文件夹
                if (isFolder) {
                    Files.createDirectories(targetFilePath);
                    return null;
                }

                // 创建文件的父目录
                currentProcessingFilePath = targetFilePath;
                Path targetFileParentPath = targetFilePath.normalize().getParent();
                if (!Files.exists(targetFileParentPath)) {
                    Files.createDirectories(targetFileParentPath);
                }

                float progress = (totalBytes > 0) ? (float) totalBytesExtracted / totalBytes : 0f;
                if (extractionListener != null) {
                    extractionListener.onProgress(String.format("正在解压: %s", filePath.toString()), progress, state);
                }

                // 返回输出流
                return new SequentialFileOutputStream(targetFilePath);
            } catch (Exception e) {
                throw new SevenZipException("Error getting stream for index " + index, e);
            }
        }

        @Override
        public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {

        }

        @Override
        public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
            closeOutputStream();
        }

        @Override
        public void setTotal(long total) throws SevenZipException {
            totalBytes = total;
        }

        @Override
        public void setCompleted(long complete) throws SevenZipException {
            totalBytesExtracted = complete;
        }

        private void closeOutputStream() throws SevenZipException {
            if (outputStream != null) {
                try {
                    outputStream.close();
                    outputStream = null;
                } catch (IOException e) {
                    throw new SevenZipException("Error closing file: "
                            + currentProcessingFilePath);
                }
            }
        }
    }

    /*
     * SevenZipJBinding 输出流实现
     */
    private static class SequentialFileOutputStream implements ISequentialOutStream {
        private final FileOutputStream fileStream;

        public SequentialFileOutputStream(Path targetFilePath) throws FileNotFoundException {
            this.fileStream = new FileOutputStream(targetFilePath.toFile());
        }

        @Override
        public int write(byte[] data) throws SevenZipException {
            try {
                fileStream.write(data);
                return data.length;
            } catch (IOException e) {
                throw new SevenZipException("Error writing to output stream", e);
            }
        }

        public void close() throws IOException {
            fileStream.close();
        }
    }
}
