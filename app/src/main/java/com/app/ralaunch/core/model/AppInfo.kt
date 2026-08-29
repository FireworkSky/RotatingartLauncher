package com.app.ralaunch.core.model

/**
 * 应用信息 - 由 Koin 从 PackageManager 构造（见 AppModule）
 */
data class AppInfo(
    val versionName: String = "Unknown",
    val versionCode: Long = 0
)
