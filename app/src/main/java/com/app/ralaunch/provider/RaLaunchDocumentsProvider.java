package com.app.ralaunch.provider;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import com.app.ralib.utils.FileUtils;

/**
 * RaLaunch 文档提供器
 * 在系统文件管理器中显示启动器的所有文件目录
 * 
 * 
 * 显示的目录结构：
 * - data/          内部数据目录 (/data/data/com.app.ralaunch/)
 * - android_data/  外部数据目录 (/Android/data/com.app.ralaunch/files/)
 * - android_obb/   OBB 目录 (/Android/obb/com.app.ralaunch/)
 * - user_de_data/  设备加密数据目录（如果存在）
 */
public class RaLaunchDocumentsProvider extends DocumentsProvider {
    
    /**
     * Authority - 必须与 AndroidManifest.xml 中一致
     */
    public static final String AUTHORITY = "com.app.ralaunch.documents";
    
    /**
     * 自定义列 - 文件的完整路径
     */
    public static final String COLUMN_FILE_PATH = "ralaunch_file_path";
    
    /**
     * 自定义列 - 文件的扩展信息（权限、符号链接等）
     */
    public static final String COLUMN_FILE_EXTRAS = "ralaunch_file_extras";
    
    /**
     * 自定义方法 - 设置文件最后修改时间
     */
    public static final String METHOD_SET_LAST_MODIFIED = "ralaunch:setLastModified";
    
    /**
     * 自定义方法 - 设置文件权限
     */
    public static final String METHOD_SET_PERMISSIONS = "ralaunch:setPermissions";
    
    /**
     * 自定义方法 - 创建符号链接
     */
    public static final String METHOD_CREATE_SYMLINK = "ralaunch:createSymlink";
    
    /**
     * 默认根目录列
     */
    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID
    };
    
    /**
     * 默认文档列
     */
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            COLUMN_FILE_EXTRAS
    };
    
    private String mPackageName;
    private File mDataDir;              // /data/data/com.app.ralaunch/
    private File mUserDeDataDir;        // /data/user_de/0/com.app.ralaunch/
    private File mAndroidDataDir;       // /Android/data/com.app.ralaunch/files/
    private File mAndroidObbDir;        // /Android/obb/com.app.ralaunch/
    
    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        
        mPackageName = context.getPackageName();
        
        // 内部数据目录
        mDataDir = context.getFilesDir().getParentFile();
        
        // 设备加密数据目录（Android 7.0+）
        String dataDirPath = mDataDir.getPath();
        if (dataDirPath.startsWith("/data/user/")) {
            mUserDeDataDir = new File("/data/user_de/" + dataDirPath.substring(11));
        }
        
        // 外部数据目录
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            mAndroidDataDir = externalFilesDir.getParentFile();
        }
        
        // OBB 目录
        mAndroidObbDir = context.getObbDir();
    }
    
    @Override
    public boolean onCreate() {
        return true;
    }
    
    /**
     * 查询根目录
     * 在系统文件管理器中显示为单个根节点
     */
    @Override
    public Cursor queryRoots(String[] projection) {
        final ApplicationInfo appInfo = getContext().getApplicationInfo();
        final String appName = appInfo.loadLabel(getContext().getPackageManager()).toString();
        
        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_ROOT_PROJECTION
        );
        
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, mPackageName);
        row.add(Root.COLUMN_DOCUMENT_ID, mPackageName);
        row.add(Root.COLUMN_SUMMARY, "RaLaunch ");
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(Root.COLUMN_TITLE, appName);
        row.add(Root.COLUMN_MIME_TYPES, "*/*");
        row.add(Root.COLUMN_ICON, appInfo.icon);
        
        return result;
    }
    
    /**
     * 查询单个文档
     */
    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION
        );
        includeFile(result, documentId, null);
        return result;
    }
    
    /**
     * 查询子文档（目录下的文件列表）
     */
    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        // 移除末尾的斜杠
        if (parentDocumentId.endsWith("/")) {
            parentDocumentId = parentDocumentId.substring(0, parentDocumentId.length() - 1);
        }
        
        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION
        );
        
        final File parent = getFileForDocId(parentDocumentId);
        
        if (parent == null) {
            // 根目录：显示所有主要目录
            includeFile(result, parentDocumentId + "/data", mDataDir);
            
            if (mAndroidDataDir != null && mAndroidDataDir.exists()) {
                includeFile(result, parentDocumentId + "/android_data", mAndroidDataDir);
            }
            
            if (mAndroidObbDir != null && mAndroidObbDir.exists()) {
                includeFile(result, parentDocumentId + "/android_obb", mAndroidObbDir);
            }
            
            if (mUserDeDataDir != null && mUserDeDataDir.exists()) {
                includeFile(result, parentDocumentId + "/user_de_data", mUserDeDataDir);
            }
        } else {
            // 子目录：列出所有文件和文件夹
            File[] files = parent.listFiles();
            if (files != null) {
                for (File file : files) {
                    includeFile(result, parentDocumentId + "/" + file.getName(), file);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 打开文档（文件）
     */
    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        final File file = getFileForDocId(documentId, false);
        if (file == null) {
            throw new FileNotFoundException(documentId + " not found");
        }
        final int fileMode = parseFileMode(mode);
        return ParcelFileDescriptor.open(file, fileMode);
    }
    
    /**
     * 解析文件打开模式
     */
    private static int parseFileMode(String mode) {
        switch (mode) {
            case "r":
                return ParcelFileDescriptor.MODE_READ_ONLY;
            case "w":
            case "wt":
                return ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE;
            case "wa":
                return ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_APPEND;
            case "rw":
                return ParcelFileDescriptor.MODE_READ_WRITE
                        | ParcelFileDescriptor.MODE_CREATE;
            case "rwt":
                return ParcelFileDescriptor.MODE_READ_WRITE
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE;
            default:
                throw new IllegalArgumentException("Invalid mode: " + mode);
        }
    }
    
    /**
     * 创建文档（新文件或目录）
     */
    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File parent = getFileForDocId(parentDocumentId);
        if (parent != null) {
            File newFile = new File(parent, displayName);
            int noConflictId = 2;
            // 如果文件名冲突，自动添加 (2), (3) 等后缀
            while (newFile.exists()) {
                newFile = new File(parent, displayName + " (" + noConflictId++ + ")");
            }
            
            try {
                boolean succeeded;
                if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                    succeeded = newFile.mkdir();
                } else {
                    succeeded = newFile.createNewFile();
                }
                
                if (succeeded) {
                    return parentDocumentId.endsWith("/") 
                            ? parentDocumentId + newFile.getName() 
                            : parentDocumentId + "/" + newFile.getName();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        throw new FileNotFoundException("Failed to create document in " + parentDocumentId);
    }
    
    /**
     * 删除文档
     */
    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        if (file == null) {
            throw new FileNotFoundException("Failed to delete document " + documentId);
        }
        
        // 对于符号链接，直接删除
        if (isSymbolicLink(file)) {
            if (!file.delete()) {
                throw new FileNotFoundException("Failed to delete document " + documentId);
            }
            return;
        }
        
        // 使用 ralib 的 FileUtils 删除目录
        boolean success = FileUtils.deleteDirectoryRecursively(Paths.get(file.getAbsolutePath()));
        if (!success) {
            throw new FileNotFoundException("Failed to delete document " + documentId);
        }
    }
    
    /**
     * 检查是否为符号链接
     */
    @SuppressWarnings("OctalInteger")
    private static boolean isSymbolicLink(File file) {
        try {
            StructStat stat = Os.lstat(file.getPath());
            // S_IFLNK = 0120000
            return (stat.st_mode & 0170000) == 0120000;
        } catch (ErrnoException e) {
            return false;
        }
    }
    
    /**
     * 从父文档中移除文档
     */
    @Override
    public void removeDocument(String documentId, String parentDocumentId) throws FileNotFoundException {
        deleteDocument(documentId);
    }
    
    /**
     * 重命名文档
     */
    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        if (file != null) {
            File target = new File(file.getParentFile(), displayName);
            if (file.renameTo(target)) {
                int i = documentId.lastIndexOf('/', documentId.length() - 2);
                return documentId.substring(0, i + 1) + displayName;
            }
        }
        throw new FileNotFoundException("Failed to rename document " + documentId);
    }
    
    /**
     * 移动文档
     */
    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        File sourceFile = getFileForDocId(sourceDocumentId);
        File targetDir = getFileForDocId(targetParentDocumentId);
        
        if (sourceFile != null && targetDir != null) {
            File targetFile = new File(targetDir, sourceFile.getName());
            if (!targetFile.exists() && sourceFile.renameTo(targetFile)) {
                return targetParentDocumentId.endsWith("/")
                        ? targetParentDocumentId + targetFile.getName()
                        : targetParentDocumentId + "/" + targetFile.getName();
            }
        }
        throw new FileNotFoundException("Failed to move document " + sourceDocumentId);
    }
    
    /**
     * 获取文档的 MIME 类型
     */
    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        return file == null ? Document.MIME_TYPE_DIR : getMimeTypeForFile(file);
    }
    
    /**
     * 检查是否为子文档
     */
    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return documentId.startsWith(parentDocumentId);
    }
    
    /**
     * 自定义方法调用（扩展功能）
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = super.call(method, arg, extras);
        if (result != null) {
            return result;
        }
        
        if (!method.startsWith("ralaunch:")) {
            return null;
        }
        
        Bundle out = new Bundle();
        try {
            Uri uri = extras.getParcelable("uri");
            List<String> pathSegments = uri.getPathSegments();
            String documentId = pathSegments.size() >= 4 ? pathSegments.get(3) : pathSegments.get(1);
            
            switch (method) {
                case METHOD_SET_LAST_MODIFIED: {
                    File file = getFileForDocId(documentId);
                    if (file == null) {
                        out.putBoolean("result", false);
                    } else {
                        long time = extras.getLong("time");
                        out.putBoolean("result", file.setLastModified(time));
                    }
                    break;
                }
                case METHOD_SET_PERMISSIONS: {
                    File file = getFileForDocId(documentId);
                    if (file == null) {
                        out.putBoolean("result", false);
                    } else {
                        int permissions = extras.getInt("permissions");
                        try {
                            Os.chmod(file.getPath(), permissions);
                            out.putBoolean("result", true);
                        } catch (ErrnoException e) {
                            out.putBoolean("result", false);
                            out.putString("message", e.getMessage());
                        }
                    }
                    break;
                }
                case METHOD_CREATE_SYMLINK: {
                    File file = getFileForDocId(documentId, false);
                    if (file == null) {
                        out.putBoolean("result", false);
                    } else {
                        String path = extras.getString("path");
                        try {
                            Os.symlink(path, file.getPath());
                            out.putBoolean("result", true);
                        } catch (ErrnoException e) {
                            out.putBoolean("result", false);
                            out.putString("message", e.getMessage());
                        }
                    }
                    break;
                }
                default:
                    out.putBoolean("result", false);
                    out.putString("message", "Unsupported method: " + method);
                    break;
            }
        } catch (Exception e) {
            out.putBoolean("result", false);
            out.putString("message", e.toString());
        }
        return out;
    }
    
    /**
     * 将文件信息添加到游标
     */
    @SuppressWarnings("OctalInteger")
    private void includeFile(MatrixCursor result, String docId, File file)
            throws FileNotFoundException {
        if (file == null) {
            file = getFileForDocId(docId);
        }
        
        // 根目录
        if (file == null) {
            final MatrixCursor.RowBuilder row = result.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, mPackageName);
            row.add(Document.COLUMN_DISPLAY_NAME, mPackageName);
            row.add(Document.COLUMN_SIZE, 0L);
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
            row.add(Document.COLUMN_LAST_MODIFIED, 0);
            row.add(Document.COLUMN_FLAGS, 0);
            return;
        }
        
        // 计算标志位
        int flags = 0;
        if (file.isDirectory()) {
            if (file.canWrite()) {
                flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
            }
        } else if (file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
        }
        
        File parentFile = file.getParentFile();
        if (parentFile != null && parentFile.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_DELETE;
            flags |= Document.FLAG_SUPPORTS_RENAME;
            flags |= Document.FLAG_SUPPORTS_MOVE;
        }
        
        // 确定显示名称
        String path = file.getPath();
        final String displayName;
        boolean addExtras = false;
        
        if (path.equals(mDataDir.getPath())) {
            displayName = "data";
        } else if (mAndroidDataDir != null && path.equals(mAndroidDataDir.getPath())) {
            displayName = "android_data";
        } else if (mAndroidObbDir != null && path.equals(mAndroidObbDir.getPath())) {
            displayName = "android_obb";
        } else if (mUserDeDataDir != null && path.equals(mUserDeDataDir.getPath())) {
            displayName = "user_de_data ";
        } else {
            displayName = file.getName();
            addExtras = true;
        }
        
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, docId);
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_MIME_TYPE, getMimeTypeForFile(file));
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(COLUMN_FILE_PATH, file.getAbsolutePath());
        
        // 添加扩展信息（权限、符号链接等）
        if (addExtras) {
            try {
                StringBuilder sb = new StringBuilder();
                StructStat stat = Os.lstat(path);
                sb.append(stat.st_mode)
                        .append("|").append(stat.st_uid)
                        .append("|").append(stat.st_gid);
                
                // 如果是符号链接，添加链接目标
                if ((stat.st_mode & 0170000) == 0120000) {
                    sb.append("|").append(Os.readlink(path));
                }
                row.add(COLUMN_FILE_EXTRAS, sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 根据文档 ID 获取文件
     * 
     * @param docId 文档 ID
     * @return 文件对象，如果是根目录则返回 null
     */
    private File getFileForDocId(String docId) throws FileNotFoundException {
        return getFileForDocId(docId, true);
    }
    
    /**
     * 根据文档 ID 获取文件
     * 
     * @param docId 文档 ID
     * @param checkExists 是否检查文件存在性
     * @return 文件对象，如果是根目录则返回 null
     */
    private File getFileForDocId(String docId, boolean checkExists) throws FileNotFoundException {
        String filename = docId;
        
        // 移除包名前缀
        if (filename.startsWith(mPackageName)) {
            filename = filename.substring(mPackageName.length());
        } else {
            throw new FileNotFoundException(docId + " not found");
        }
        
        // 移除开头的斜杠
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        
        // 根目录
        if (filename.isEmpty()) {
            return null;
        }
        
        // 解析路径类型和子路径
        String type;
        String subPath;
        int i = filename.indexOf('/');
        if (i == -1) {
            type = filename;
            subPath = "";
        } else {
            type = filename.substring(0, i);
            subPath = filename.substring(i + 1);
        }
        
        // 根据类型获取基础目录
        File file = null;
        if (type.equalsIgnoreCase("data")) {
            file = new File(mDataDir, subPath);
        } else if (type.equalsIgnoreCase("android_data") && mAndroidDataDir != null) {
            file = new File(mAndroidDataDir, subPath);
        } else if (type.equalsIgnoreCase("android_obb") && mAndroidObbDir != null) {
            file = new File(mAndroidObbDir, subPath);
        } else if (type.equalsIgnoreCase("user_de_data") && mUserDeDataDir != null) {
            file = new File(mUserDeDataDir, subPath);
        }
        
        if (file == null) {
            throw new FileNotFoundException(docId + " not found");
        }
        
        // 检查文件是否存在（使用 lstat 支持符号链接）
        if (checkExists) {
            try {
                Os.lstat(file.getPath());
            } catch (Exception e) {
                throw new FileNotFoundException(docId + " not found: " + e.getMessage());
            }
        }
        
        return file;
    }
    
    /**
     * 获取文件的 MIME 类型
     */
    private static String getMimeTypeForFile(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        }
        
        final String name = file.getName();
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1).toLowerCase();
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        
        return "application/octet-stream";
    }
}
