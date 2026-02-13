package com.app.ralaunch.shared.data.local

/**
 * 存储路径提供器。
 *
 * 使用 expect/actual 由平台提供目录路径，具体业务层文件操作由 commonMain 处理。
 */
expect class StoragePathsProvider {
    fun gamesDirPathFull(): String
    fun controlLayoutsDirPathFull(): String
    fun settingsFilePathFull(): String
}
