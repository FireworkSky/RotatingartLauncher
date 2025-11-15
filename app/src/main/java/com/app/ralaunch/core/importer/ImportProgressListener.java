package com.app.ralaunch.core.importer;

/**
 * 游戏导入进度监听器
 *
 * 用于监听游戏导入过程中的各个阶段
 */
public interface ImportProgressListener {
    /**
     * 导入进度更新
     * @param message 当前操作描述
     * @param progress 进度百分比 (0-100)
     */
    void onProgress(String message, int progress);

    /**
     * 导入完成
     * @param gamePath 游戏主程序路径
     * @param modLoaderPath ModLoader路径(可为null)
     */
    void onComplete(String gamePath, String modLoaderPath);

    /**
     * 导入出错
     * @param error 错误信息
     */
    void onError(String error);

    /**
     * 导入取消
     */
    void onCancelled();
}
