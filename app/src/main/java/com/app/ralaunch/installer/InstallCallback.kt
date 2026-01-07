package com.app.ralaunch.installer

/**
 * 安装回调接口
 */
interface InstallCallback {
    /**
     * 安装进度回调
     * @param message 进度消息
     * @param progress 进度值 (0-100)
     */
    fun onProgress(message: String, progress: Int)
    
    /**
     * 安装完成回调
     * @param gamePath 游戏目录路径（包含游戏文件的实际目录）
     * @param gameBasePath 游戏根目录路径（用于删除时删除整个安装目录）
     * @param gameName 游戏名称
     * @param launchTarget 启动目标（如 tModLoader.dll 或 StardewModdingAPI.dll）
     * @param iconPath 图标路径
     */
    fun onComplete(
        gamePath: String,
        gameBasePath: String,
        gameName: String,
        launchTarget: String?,
        iconPath: String?
    )
    
    /**
     * 安装错误回调
     * @param error 错误消息
     */
    fun onError(error: String)
    
    /**
     * 安装取消回调
     */
    fun onCancelled()
}
