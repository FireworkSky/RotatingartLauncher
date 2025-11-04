/**
 * @file jni_bridge.h
 * @brief JNI 桥接器
 * 
 * 此模块提供 Java 和 Native 代码之间的桥接功能，包括：
 * - JVM 生命周期管理
 * - JNI 环境获取和线程附加
 * - 从 Native 回调到 Java
 * - 启动参数设置
 */
#pragma once

#include <jni.h>

/**
 * @brief JNI_OnLoad 生命周期回调
 * 
 * @param vm JavaVM 指针
 * @return JNI 版本号
 * 
 * 在动态库被加载时由 JVM 调用，用于初始化 JNI 环境。
 */
jint Bridge_JNI_OnLoad(JavaVM* vm);

/**
 * @brief JNI_OnUnload 生命周期回调
 * 
 * @param vm JavaVM 指针
 * 
 * 在动态库被卸载时由 JVM 调用，用于清理资源。
 */
void Bridge_JNI_OnUnload(JavaVM* vm);

/**
 * @brief 获取当前线程的 JNI 环境
 * 
 * @return JNIEnv 指针，失败返回 NULL
 * 
 * 如果当前线程未附加到 JVM，此函数会自动附加。
 * 附加的线程需要在退出前调用 Bridge_SafeDetachJNIEnv() 分离。
 */
JNIEnv* Bridge_GetJNIEnv();

/**
 * @brief 安全地从 JVM 分离当前线程
 * 
 * 如果当前线程是通过 Bridge_GetJNIEnv() 附加的，此函数会将其分离。
 * 对于已经附加的线程（如 Java 创建的线程），此函数不执行任何操作。
 */
void Bridge_SafeDetachJNIEnv();

/**
 * @brief 获取全局 JavaVM 指针
 * 
 * @return JavaVM 指针，如果未初始化则返回 NULL
 * 
 * 用于其他模块（如 .NET 加密库）获取 JavaVM 以初始化 JNI 环境。
 */
JavaVM* Bridge_GetJavaVM();

/**
 * @brief 通知 Java 层游戏已退出
 * 
 * @param exitCode 游戏退出码
 * 
 * 从 Native 代码调用 Java 层的 GameActivity.onGameExit() 静态方法。
 */
void Bridge_NotifyGameExit(int exitCode);

/**
 * @brief JNI 函数：设置启动参数（基础版本）
 * 
 * @param env JNI 环境指针
 * @param clazz Java 类引用
 * @param appPath 应用程序主程序集路径
 * @param dotnetPath .NET 运行时路径
 */
void Java_com_app_ralaunch_game_GameLauncher_setLaunchParams(
    JNIEnv *env, jclass clazz, jstring appPath, jstring dotnetPath);

/**
 * @brief JNI 函数：设置启动参数（包含运行时版本）
 * 
 * @param env JNI 环境指针
 * @param clazz Java 类引用
 * @param appPath 应用程序主程序集路径
 * @param dotnetPath .NET 运行时路径
 * @param frameworkVersion 指定的框架版本
 */
void Java_com_app_ralaunch_game_GameLauncher_setLaunchParamsWithRuntime(
    JNIEnv *env, jclass clazz, jstring appPath, jstring dotnetPath, jstring frameworkVersion);

/**
 * @brief JNI 函数：设置Bootstrap启动参数
 * 
 * @param env JNI 环境指针
 * @param clazz Java 类引用
 * @param bootstrapDll Bootstrap程序集路径
 * @param targetGameAssembly 目标游戏程序集路径
 * @param dotnetPath .NET 运行时路径
 */
void Java_com_app_ralaunch_game_GameLauncher_setBootstrapLaunchParams(
    JNIEnv *env, jclass clazz, jstring bootstrapDll, jstring targetGameAssembly, jstring dotnetPath);


