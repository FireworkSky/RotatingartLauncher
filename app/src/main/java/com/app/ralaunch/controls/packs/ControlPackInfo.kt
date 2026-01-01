package com.app.ralaunch.controls.packs

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 控件包分类定义
 * 从仓库配置中动态加载
 */
@Serializable
data class PackCategory(
    /** 分类唯一标识 */
    val id: String,
    
    /** 显示名称 */
    val name: String,
    
    /** Material Icon 名称 */
    val icon: String = "apps",
    
    /** 排序优先级 (越小越靠前) */
    val order: Int = 0
) {
    companion object {
        /** 默认"全部"分类 */
        val ALL = PackCategory(
            id = "all",
            name = "全部",
            icon = "apps",
            order = -1
        )
    }
}

/**
 * 控件包元数据信息
 * 描述一个控件包的基本信息，用于展示和管理
 */
@Serializable
data class ControlPackInfo(
    /** 唯一标识符 */
    val id: String,
    
    /** 控件包名称 */
    val name: String,
    
    /** 作者名称 */
    val author: String = "",
    
    /** 版本号 (语义化版本) */
    val version: String = "1.0.0",
    
    /** 版本代码 (用于比较更新) */
    val versionCode: Int = 1,
    
    /** 控件包介绍/描述 */
    val description: String = "",
    
    /** 支持的设备类型: phone, tablet, all */
    val deviceType: String = "all",
    
    /** 所属分类ID */
    val category: String = "all",
    
    /** 控件包标签 */
    val tags: List<String> = emptyList(),
    
    /** 图标路径 (相对路径) */
    val iconPath: String = "",
    
    /** 预览截图列表 (相对路径) */
    val previewImagePaths: List<String> = emptyList(),
    
    /** 纹理资源文件列表 (相对于 assets 目录的路径) */
    val assetFiles: List<String> = emptyList(),
    
    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 更新时间戳 */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** 下载次数 (远程仓库统计) */
    val downloadCount: Int = 0,
    
    /** 文件大小 (字节) */
    val fileSize: Long = 0,
    
    /** 下载 URL (远程包使用) */
    val downloadUrl: String = "",
    
    /** 最低支持的启动器版本 */
    val minLauncherVersion: String = "1.0.0",
    
    /** 是否为本地包（非远程下载） */
    @Transient
    val isLocal: Boolean = true
) {
    companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val LAYOUT_FILE_NAME = "layout.json"
        const val ICON_FILE_NAME = "icon.png"
        const val ASSETS_DIR_NAME = "assets"
        
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        
        fun fromJson(jsonString: String): ControlPackInfo? {
            return try {
                json.decodeFromString<ControlPackInfo>(jsonString)
            } catch (e: Exception) {
                android.util.Log.e("ControlPackInfo", "Failed to parse JSON: ${e.message}", e)
                null
            }
        }
        
        fun fromFile(file: File): ControlPackInfo? {
            if (!file.exists()) return null
            return fromJson(file.readText())
        }
    }
    
    fun toJson(): String = json.encodeToString(this)
    
    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(toJson())
    }
}

/**
 * 控件包状态
 */
enum class ControlPackStatus {
    /** 未安装 */
    NOT_INSTALLED,
    /** 已安装 */
    INSTALLED,
    /** 有更新可用 */
    UPDATE_AVAILABLE,
    /** 下载中 */
    DOWNLOADING,
    /** 安装中 */
    INSTALLING
}

/**
 * 控件包列表项 - 用于 UI 展示
 */
data class ControlPackItem(
    val info: ControlPackInfo,
    val status: ControlPackStatus,
    val installedVersion: String? = null,
    val localIconPath: String? = null,
    val progress: Int = 0
)

/**
 * 远程仓库索引
 */
@Serializable
data class ControlPackRepository(
    /** 仓库版本 */
    val version: Int = 1,
    
    /** 仓库名称 */
    val name: String = "",
    
    /** 仓库描述 */
    val description: String = "",
    
    /** 仓库作者 */
    val author: String = "",
    
    /** 仓库网站 */
    val website: String = "",
    
    /** 最后更新时间 (ISO 8601 格式字符串) */
    val lastUpdated: String = "",
    
    /** 分类定义列表 */
    val categories: List<PackCategory> = emptyList(),
    
    /** 控件包列表 */
    val packs: List<ControlPackInfo> = emptyList(),
    
    /** 仓库公告 */
    val announcement: String = ""
)
