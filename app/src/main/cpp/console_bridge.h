/**
 * @file console_bridge.h
 * @brief C# Console 重定向桥接器
 * 
 * 将 C# 的 Console.WriteLine/Console.Read 重定向到 Android Java 控制台 UI
 */

#ifndef CONSOLE_BRIDGE_H
#define CONSOLE_BRIDGE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief 初始化 Console 重定向桥接器
 * @param env JNI 环境指针
 * @return 0 成功，-1 失败
 */
int Console_Bridge_Init(JNIEnv* env);

/**
 * @brief 从 C# 接收 Console.WriteLine 输出
 * @param text 输出文本（UTF-8）
 */
void Console_Bridge_WriteOutput(const char* text);

/**
 * @brief 从 C# 接收 Console.ReadLine 请求（阻塞直到有输入）
 * @param buffer 输入缓冲区
 * @param buffer_size 缓冲区大小
 * @return 实际读取的字节数，-1 表示失败
 */
int Console_Bridge_ReadInput(char* buffer, int buffer_size);

/**
 * @brief JNI 方法：设置 Console 输出回调
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_console_ConsoleService_nativeSetConsoleOutputCallback(
    JNIEnv* env, jclass clazz);

/**
 * @brief JNI 方法：设置 Console 输入回调
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_console_ConsoleService_nativeSetConsoleInputCallback(
    JNIEnv* env, jclass clazz);

#ifdef __cplusplus
}
#endif

#endif // CONSOLE_BRIDGE_H

