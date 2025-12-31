package com.app.ralaunch.controls.packs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.app.ralaunch.utils.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 控件包管理器
 * 
 * 统一管理控件包的安装、卸载、导入导出等操作
 * 整合了原有的 ControlConfigManager 功能
 * 
 * 存储结构:
 * - RALaunch/
 *   - control_packs/
 *     - {pack_id}/
 *       - manifest.json    (控件包元数据)
 *       - icon.png         (图标)
 *       - layout.json      (布局配置)
 *       - assets/          (纹理资源)
 *   - pack_manager.json    (管理器状态)
 */
class ControlPackManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ControlPackManager"
        
        /** SD卡存储目录名 */
        const val STORAGE_DIR_NAME = "RALauncher"
        
        /** 控件包根目录 */
        const val PACKS_ROOT_DIR = "ControlPacks"
        
        /** 已安装的控件包目录 */
        const val INSTALLED_DIR_NAME = "installed"
        
        /** 下载缓存目录 */
        const val DOWNLOADS_DIR_NAME = "downloads"
        
        /** 默认/预设控件包目录 */
        const val PRESETS_DIR_NAME = "presets"
        
        /** 管理器状态文件 */
        const val MANAGER_STATE_FILE = "pack_manager.json"
        
        /** 控件包文件扩展名 */
        const val PACK_EXTENSION = ".ralpack"
        
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    
    /**
     * 管理器状态
     */
    @Serializable
    data class ManagerState(
        var selectedPackId: String? = null,
        var lastModified: Long = System.currentTimeMillis()
    )
    
    /** 获取SD卡根目录下的 RALauncher 目录 */
    val storageDir: File
        get() {
            val sdCard = Environment.getExternalStorageDirectory()
            return File(sdCard, STORAGE_DIR_NAME).also {
                if (!it.exists()) it.mkdirs()
            }
        }
    
    /** 控件包根目录: RALauncher/ControlPacks/ */
    val packsRootDir: File
        get() = File(storageDir, PACKS_ROOT_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    
    /** 已安装的控件包目录: RALauncher/ControlPacks/installed/ */
    val packsDir: File
        get() = File(packsRootDir, INSTALLED_DIR_NAME).also {
            if (!it.exists()) it.mkdirs()
        }
    
    /** 下载缓存目录: RALauncher/ControlPacks/downloads/ */
    val downloadsDir: File
        get() = File(packsRootDir, DOWNLOADS_DIR_NAME).also {
            if (!it.exists()) it.mkdirs()
        }
    
    /** 预设控件包目录: RALauncher/ControlPacks/presets/ */
    val presetsDir: File
        get() = File(packsRootDir, PRESETS_DIR_NAME).also {
            if (!it.exists()) it.mkdirs()
        }
    
    /** 管理器状态文件 */
    private val managerStateFile: File
        get() = File(packsRootDir, MANAGER_STATE_FILE)
    
    // ========== 状态管理 ==========
    
    /**
     * 加载管理器状态
     */
    private fun loadManagerState(): ManagerState {
        if (!managerStateFile.exists()) {
            return ManagerState()
        }
        return try {
            val content = managerStateFile.readText()
            json.decodeFromString<ManagerState>(content)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to load manager state", e)
            ManagerState()
        }
    }
    
    /**
     * 保存管理器状态
     */
    private fun saveManagerState(state: ManagerState) {
        try {
            managerStateFile.writeText(json.encodeToString(state))
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to save manager state", e)
        }
    }
    
    /**
     * 获取当前选中的控件包 ID
     */
    fun getSelectedPackId(): String? {
        val state = loadManagerState()
        val packIds = listPackIds()
        
        // 如果选中的包不存在，选择第一个
        if (state.selectedPackId == null || state.selectedPackId !in packIds) {
            val firstId = packIds.firstOrNull()
            setSelectedPackId(firstId)
            return firstId
        }
        return state.selectedPackId
    }
    
    /**
     * 设置当前选中的控件包 ID
     */
    fun setSelectedPackId(packId: String?) {
        val state = ManagerState(
            selectedPackId = packId,
            lastModified = System.currentTimeMillis()
        )
        saveManagerState(state)
    }
    
    // ========== 包管理 ==========
    
    /**
     * 获取所有已安装的控件包 ID 列表
     */
    fun listPackIds(): List<String> {
        val dirs = packsDir.listFiles { file -> 
            file.isDirectory && File(file, ControlPackInfo.MANIFEST_FILE_NAME).exists()
        }
        return dirs?.map { it.name } ?: emptyList()
    }
    
    /**
     * 获取所有已安装的控件包
     */
    fun getInstalledPacks(): List<ControlPackInfo> {
        val packs = mutableListOf<ControlPackInfo>()
        
        AppLogger.debug(TAG, "Loading installed packs from: ${packsDir.absolutePath}")
        
        if (!packsDir.exists()) {
            AppLogger.warn(TAG, "Packs directory does not exist: ${packsDir.absolutePath}")
            return packs
        }
        
        val dirs = packsDir.listFiles { file -> file.isDirectory }
        if (dirs == null || dirs.isEmpty()) {
            AppLogger.debug(TAG, "No pack directories found")
            return packs
        }
        
        AppLogger.debug(TAG, "Found ${dirs.size} directories")
        
        for (dir in dirs) {
            val manifestFile = File(dir, ControlPackInfo.MANIFEST_FILE_NAME)
            AppLogger.debug(TAG, "Checking: ${manifestFile.absolutePath}, exists=${manifestFile.exists()}")
            
            if (manifestFile.exists()) {
                try {
                    val content = manifestFile.readText()
                    AppLogger.debug(TAG, "Manifest content length: ${content.length}")
                    
                    val info = ControlPackInfo.fromJson(content)
                    if (info != null) {
                        packs.add(info)
                        AppLogger.info(TAG, "Loaded pack: ${info.id} - ${info.name}")
                    } else {
                        AppLogger.error(TAG, "Failed to parse manifest: ${dir.name}")
                    }
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Failed to load pack manifest: ${dir.name}", e)
                }
            }
        }
        
        AppLogger.info(TAG, "Total loaded packs: ${packs.size}")
        return packs
    }
    
    /**
     * 检查控件包是否已安装
     */
    fun isPackInstalled(packId: String): Boolean {
        val packDir = File(packsDir, packId)
        val manifestFile = File(packDir, ControlPackInfo.MANIFEST_FILE_NAME)
        return manifestFile.exists()
    }
    
    /**
     * 获取控件包信息
     */
    fun getPackInfo(packId: String): ControlPackInfo? {
        val packDir = File(packsDir, packId)
        val manifestFile = File(packDir, ControlPackInfo.MANIFEST_FILE_NAME)
        return ControlPackInfo.fromFile(manifestFile)
    }
    
    /**
     * 获取控件包目录
     */
    fun getPackDir(packId: String): File = File(packsDir, packId)
    
    /**
     * 获取控件包的布局配置
     */
    fun getPackLayout(packId: String): ControlLayout? {
        val packDir = File(packsDir, packId)
        val layoutFile = File(packDir, ControlPackInfo.LAYOUT_FILE_NAME)
        
        if (!layoutFile.exists()) return null
        
        return try {
            ControlLayout.loadFrom(layoutFile)?.also { it.id = packId }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to load pack layout: $packId", e)
            null
        }
    }
    
    /**
     * 获取当前选中的布局配置
     */
    fun getCurrentLayout(): ControlLayout? {
        val packId = getSelectedPackId() ?: return null
        return getPackLayout(packId)
    }
    
    /**
     * 保存布局到指定包
     */
    fun savePackLayout(packId: String, layout: ControlLayout) {
        val packDir = File(packsDir, packId)
        packDir.mkdirs()
        
        val layoutFile = File(packDir, ControlPackInfo.LAYOUT_FILE_NAME)
        layout.saveTo(layoutFile)
        
        // 更新 manifest
        val manifestFile = File(packDir, ControlPackInfo.MANIFEST_FILE_NAME)
        val info = if (manifestFile.exists()) {
            ControlPackInfo.fromFile(manifestFile)?.copy(
                name = layout.name,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            ControlPackInfo(
                id = packId,
                name = layout.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        info?.saveTo(manifestFile)
        
        AppLogger.info(TAG, "Saved layout to pack: $packId")
    }
    
    /**
     * 创建新的控件包
     */
    fun createPack(name: String, author: String = "", description: String = ""): ControlPackInfo {
        val packId = "pack_${System.currentTimeMillis()}"
        val packDir = File(packsDir, packId)
        packDir.mkdirs()
        
        val info = ControlPackInfo(
            id = packId,
            name = name,
            author = author,
            description = description,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // 保存 manifest
        info.saveTo(File(packDir, ControlPackInfo.MANIFEST_FILE_NAME))
        
        // 创建空布局
        val layout = ControlLayout(name = name)
        layout.id = packId
        layout.saveTo(File(packDir, ControlPackInfo.LAYOUT_FILE_NAME))
        
        AppLogger.info(TAG, "Created new pack: $packId ($name)")
        return info
    }
    
    /**
     * 删除控件包
     */
    fun deletePack(packId: String): Boolean {
        val packDir = File(packsDir, packId)
        if (!packDir.exists()) return false
        
        return try {
            packDir.deleteRecursively()
            
            // 如果删除的是当前选中的包，选择其他包
            if (getSelectedPackId() == packId) {
                setSelectedPackId(listPackIds().firstOrNull())
            }
            
            AppLogger.info(TAG, "Deleted pack: $packId")
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to delete pack: $packId", e)
            false
        }
    }
    
    /**
     * 复制控件包
     */
    fun duplicatePack(packId: String, newName: String): ControlPackInfo? {
        val sourceLayout = getPackLayout(packId) ?: return null
        val sourceInfo = getPackInfo(packId) ?: return null
        
        val newPack = createPack(
            name = newName,
            author = sourceInfo.author,
            description = sourceInfo.description
        )
        
        // 复制布局
        val newLayout = sourceLayout.deepCopy()
        newLayout.id = newPack.id
        newLayout.name = newName
        savePackLayout(newPack.id, newLayout)
        
        // 复制资源
        val sourceAssetsDir = File(File(packsDir, packId), ControlPackInfo.ASSETS_DIR_NAME)
        val targetAssetsDir = File(File(packsDir, newPack.id), ControlPackInfo.ASSETS_DIR_NAME)
        if (sourceAssetsDir.exists()) {
            sourceAssetsDir.copyRecursively(targetAssetsDir, overwrite = true)
        }
        
        // 复制图标
        val sourceIcon = File(File(packsDir, packId), ControlPackInfo.ICON_FILE_NAME)
        val targetIcon = File(File(packsDir, newPack.id), ControlPackInfo.ICON_FILE_NAME)
        if (sourceIcon.exists()) {
            sourceIcon.copyTo(targetIcon, overwrite = true)
        }
        
        AppLogger.info(TAG, "Duplicated pack: $packId -> ${newPack.id}")
        return getPackInfo(newPack.id)
    }
    
    /**
     * 重命名控件包
     */
    fun renamePack(packId: String, newName: String): Boolean {
        val info = getPackInfo(packId) ?: return false
        val layout = getPackLayout(packId) ?: return false
        
        // 更新 manifest
        val newInfo = info.copy(name = newName, updatedAt = System.currentTimeMillis())
        newInfo.saveTo(File(File(packsDir, packId), ControlPackInfo.MANIFEST_FILE_NAME))
        
        // 更新布局名称
        layout.name = newName
        savePackLayout(packId, layout)
        
        return true
    }
    
    // ========== 图标管理 ==========
    
    /**
     * 获取控件包的图标
     */
    fun getPackIcon(packId: String): Bitmap? {
        val packDir = File(packsDir, packId)
        val iconFile = File(packDir, ControlPackInfo.ICON_FILE_NAME)
        
        if (!iconFile.exists()) return null
        
        return try {
            BitmapFactory.decodeFile(iconFile.absolutePath)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取控件包的图标文件路径
     */
    fun getPackIconPath(packId: String): String? {
        val packDir = File(packsDir, packId)
        val iconFile = File(packDir, ControlPackInfo.ICON_FILE_NAME)
        return if (iconFile.exists()) iconFile.absolutePath else null
    }
    
    // ========== 纹理资源管理 ==========
    
    /**
     * 获取控件包的纹理资源目录
     */
    fun getPackAssetsDir(packId: String): File? {
        val packDir = File(packsDir, packId)
        val assetsDir = File(packDir, ControlPackInfo.ASSETS_DIR_NAME)
        return if (assetsDir.exists() && assetsDir.isDirectory) assetsDir else null
    }
    
    /**
     * 获取所有纹理文件路径
     */
    fun getPackTextures(packId: String): List<String> {
        val assetsDir = getPackAssetsDir(packId) ?: return emptyList()
        
        return assetsDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }
            .map { it.absolutePath }
            .toList()
    }
    
    // ========== 导入导出 ==========
    
    /**
     * 从 .ralpack 文件安装控件包
     */
    fun installFromFile(packFile: File): Result<ControlPackInfo> {
        return try {
            ZipFile(packFile).use { zip ->
                // 读取 manifest
                val manifestEntry = zip.getEntry(ControlPackInfo.MANIFEST_FILE_NAME)
                    ?: return Result.failure(Exception("Invalid pack: missing manifest.json"))
                
                val manifestContent = zip.getInputStream(manifestEntry).bufferedReader().readText()
                val info = ControlPackInfo.fromJson(manifestContent)
                    ?: return Result.failure(Exception("Invalid manifest.json"))
                
                // 创建目标目录
                val packDir = File(packsDir, info.id)
                if (packDir.exists()) {
                    packDir.deleteRecursively()
                }
                packDir.mkdirs()
                
                // 解压所有文件
                zip.entries().asSequence().forEach { entry ->
                    val targetFile = File(packDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                
                AppLogger.info(TAG, "Installed pack from file: ${info.name} (${info.id})")
                Result.success(info)
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to install pack from file", e)
            Result.failure(e)
        }
    }
    
    /**
     * 从 JSON 文件导入布局（兼容旧格式）
     */
    fun importFromJson(jsonFile: File, name: String? = null): Result<ControlPackInfo> {
        return try {
            val layout = ControlLayout.loadFrom(jsonFile)
                ?: return Result.failure(Exception("Invalid layout file"))
            
            val packName = name ?: layout.name
            val info = createPack(packName)
            
            layout.id = info.id
            layout.name = packName
            savePackLayout(info.id, layout)
            
            AppLogger.info(TAG, "Imported layout from JSON: $packName")
            Result.success(getPackInfo(info.id)!!)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to import from JSON", e)
            Result.failure(e)
        }
    }
    
    /**
     * 从 JSON 字符串导入布局（兼容旧格式）
     */
    fun importFromJsonString(jsonString: String, name: String? = null): Result<ControlPackInfo> {
        return try {
            val layout = ControlLayout.loadFromJson(jsonString)
                ?: return Result.failure(Exception("Invalid layout JSON"))
            
            val packName = name ?: layout.name
            val info = createPack(packName)
            
            layout.id = info.id
            layout.name = packName
            savePackLayout(info.id, layout)
            
            AppLogger.info(TAG, "Imported layout from JSON string: $packName")
            Result.success(getPackInfo(info.id)!!)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to import from JSON string", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导出控件包为 .ralpack 文件
     */
    fun exportToFile(packId: String, outputFile: File): Result<File> {
        return try {
            val packDir = File(packsDir, packId)
            if (!packDir.exists()) {
                return Result.failure(Exception("Pack not found: $packId"))
            }
            
            ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
                packDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entryName = file.relativeTo(packDir).path.replace('\\', '/')
                        zip.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            
            AppLogger.info(TAG, "Exported pack to file: $packId -> ${outputFile.path}")
            Result.success(outputFile)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to export pack", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导出布局为 JSON 文件（兼容旧格式）
     */
    fun exportLayoutToJson(packId: String, outputFile: File): Result<File> {
        return try {
            val layout = getPackLayout(packId)
                ?: return Result.failure(Exception("Pack not found: $packId"))
            
            layout.saveTo(outputFile)
            
            AppLogger.info(TAG, "Exported layout to JSON: $packId -> ${outputFile.path}")
            Result.success(outputFile)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to export layout to JSON", e)
            Result.failure(e)
        }
    }
    
    // ========== 别名方法 ==========
    
    /**
     * 安装控件包（从文件）
     * 别名方法，与 installFromFile 相同
     */
    fun installPack(packFile: File): Result<ControlPackInfo> = installFromFile(packFile)
    
    /**
     * 卸载控件包
     * 别名方法，与 deletePack 相同
     */
    fun uninstallPack(packId: String): Boolean = deletePack(packId)
}
