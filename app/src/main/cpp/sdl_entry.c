

#include <jni.h>
#include <android/log.h>
#include "netcorehost_launcher.h"
#include "jni_bridge.h"
#include "app_logger.h"


#define LOG_TAG "GameLauncher"
// 使用 app_logger 以支持文件日志
#define LOGI(...) app_logger_log(LOG_LEVEL_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) app_logger_log(LOG_LEVEL_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) app_logger_log(LOG_LEVEL_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * @brief JNI_OnLoad 回调
 *
 * @param vm JavaVM 指针
 * @param reserved 保留参数（未使用）
 * @return JNI 版本号
 *
 * SDL 库加载时的初始化回调。

 */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;

    // Hook安装推迟到SDL_main，避免过早安装导致崩溃

    return Bridge_JNI_OnLoad(vm);
}

/**
 * @brief JNI_OnUnload 回调
 * 
 * @param vm JavaVM 指针
 * @param reserved 保留参数（未使用）
 * 
 * SDL 库卸载时的清理回调。清理 JNI bridge 资源。
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)reserved;
    Bridge_JNI_OnUnload(vm);
}

/**
 * @brief SDL 主函数入口点
 *
 * @param argc 参数数量（未使用）
 * @param argv 参数数组（未使用）
 * @return 应用程序退出码
 *
 * 这是 .NET 应用程序的主入口点。所有启动参数已经由 Java 层
 * 通过 JNI 调用预先设置（见 GameLauncher.netcorehostSetParams），
 * 因此这里直接调用 netcorehost 启动函数。
 *
 * 调用流程：
 * 1. Java SDLActivity 启动
 * 2. Java 调用 GameLauncher.netcorehostSetParams() 设置参数
 * 3. SDL 调用此 SDL_main 函数
 * 4. 此函数调用 netcorehost_launch()
 * 5. netcorehost API 加载并执行 .NET 应用程序
 */
__attribute__((visibility("default"))) int SDL_main(int argc, char* argv[]) {
    (void)argc;
    (void)argv;

    LOGI("================================================");
    LOGI("SDL_main started (using netcorehost API)");
    LOGI("================================================");

    // 使用 netcorehost API 启动 .NET 应用程序
    int result = netcorehost_launch();

    LOGI("================================================");
    LOGI(".NET execution finished with result: %d", result);
    LOGI("================================================");

    // 获取错误消息(如果有)
    const char* error_message = netcorehost_get_last_error();

    // 清理资源
    netcorehost_cleanup();

    // 通知 Java 层游戏已退出(带错误消息)
    Bridge_NotifyGameExitWithMessage(result, error_message);

    return result;
}


