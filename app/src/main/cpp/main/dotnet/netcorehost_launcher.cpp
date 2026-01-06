/**
 * @file netcorehost_launcher.cpp
 * @brief 简化的 .NET 启动器实现（直接使用 run_app）
 * 
 * 此文件实现了简化的 .NET 应用启动流程，直接使用 hostfxr->run_app()
 */

#include "netcorehost_launcher.h"
#include "corehost_trace_redirect.h"
#include "thread_affinity_manager.h"
#include <netcorehost/nethost.hpp>
#include <netcorehost/hostfxr.hpp>
#include <netcorehost/context.hpp>
#include <netcorehost/error.hpp>
#include <netcorehost/bindings.hpp>
#include <netcorehost/delegate_loader.hpp>
#include <jni.h>
#include <dirent.h>
#include <dlfcn.h>
#include <vector>

#include "dotnet/dotnet_launcher.hpp"
#include "shared_envvars.hpp"

// 直接声明静态链接的 nethost 函数
extern "C" {
int32_t get_hostfxr_path(
        char* buffer,
        size_t* buffer_size,
        const netcorehost::bindings::get_hostfxr_parameters* parameters
);
JNIEnv* Bridge_GetJNIEnv();
JavaVM* Bridge_GetJavaVM();
}

#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <format>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <string>
#include <cassert>
#include "app_logger.h"

#define LOG_TAG "NetCoreHost"

/**
 * @brief 通用进程启动器 - 供 .NET P/Invoke 调用
 * 
 * @param assembly_path 程序集完整路径
 * @param args_json     命令行参数 JSON 数组（如 ["-server", "-world", "xxx"]）
 * @param startup_hooks DOTNET_STARTUP_HOOKS 值，可为 nullptr
 * @param title         通知标题
 * @return 0 成功，非0 失败
 */
extern "C" __attribute__((visibility("default"))) 
int process_launcher_start(const char* assembly_path, const char* args_json, 
                           const char* startup_hooks, const char* title) {
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "process_launcher_start called");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  Assembly: %s", assembly_path ? assembly_path : "(null)");
    LOGI(LOG_TAG, "  Args JSON: %s", args_json ? args_json : "(null)");
    LOGI(LOG_TAG, "  StartupHooks: %s", startup_hooks ? "yes" : "no");
    LOGI(LOG_TAG, "  Title: %s", title ? title : "(null)");
    
    if (!assembly_path) {
        LOGE(LOG_TAG, "Assembly path is null");
        return -1;
    }
    
    JNIEnv* env = Bridge_GetJNIEnv();
    if (env == nullptr) {
        LOGE(LOG_TAG, "Failed to get JNIEnv");
        return -2;
    }
    
    // 解析 JSON 数组为 String[]
    // 简单解析：假设格式为 ["arg1","arg2",...]
    jobjectArray jArgs = nullptr;
    if (args_json && args_json[0] == '[') {
        // 简单的 JSON 数组解析
        std::vector<std::string> args;
        std::string json(args_json);
        size_t pos = 1; // 跳过 '['
        
        while (pos < json.length()) {
            // 跳过空白
            while (pos < json.length() && (json[pos] == ' ' || json[pos] == ',')) pos++;
            if (pos >= json.length() || json[pos] == ']') break;
            
            if (json[pos] == '"') {
                pos++; // 跳过开始引号
                std::string arg;
                while (pos < json.length() && json[pos] != '"') {
                    if (json[pos] == '\\' && pos + 1 < json.length()) {
                        pos++;
                        if (json[pos] == 'n') arg += '\n';
                        else if (json[pos] == 't') arg += '\t';
                        else arg += json[pos];
                    } else {
                        arg += json[pos];
                    }
                    pos++;
                }
                pos++; // 跳过结束引号
                args.push_back(arg);
            } else {
                pos++;
            }
        }
        
        if (!args.empty()) {
            jclass stringClass = env->FindClass("java/lang/String");
            jArgs = env->NewObjectArray(args.size(), stringClass, nullptr);
            for (size_t i = 0; i < args.size(); i++) {
                jstring jArg = env->NewStringUTF(args[i].c_str());
                env->SetObjectArrayElement(jArgs, i, jArg);
                env->DeleteLocalRef(jArg);
            }
            LOGI(LOG_TAG, "  Parsed %zu arguments", args.size());
        }
    }
    
    // 转换为 Java 字符串
    jstring jAssemblyPath = env->NewStringUTF(assembly_path);
    jstring jStartupHooks = startup_hooks ? env->NewStringUTF(startup_hooks) : nullptr;
    jstring jTitle = env->NewStringUTF(title ? title : "Process");
    
    // 获取 ProcessLauncherService 类
    jclass serviceClass = env->FindClass("com/app/ralaunch/service/ProcessLauncherService");
    if (serviceClass == nullptr) {
        LOGE(LOG_TAG, "Failed to find ProcessLauncherService class");
        // 清理已分配的资源
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -3;
    }
    
    // 获取 launch 静态方法
    jmethodID launchMethod = env->GetStaticMethodID(serviceClass, "launch",
        "(Landroid/content/Context;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (launchMethod == nullptr) {
        LOGE(LOG_TAG, "Failed to find launch method");
        // 清理已分配的资源
        env->DeleteLocalRef(serviceClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -4;
    }
    
    // 获取 Context
    jclass sdlActivityClass = env->FindClass("org/libsdl/app/SDLActivity");
    if (sdlActivityClass == nullptr) {
        LOGE(LOG_TAG, "Failed to find SDLActivity class");
        // 清理已分配的资源
        env->DeleteLocalRef(serviceClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -6;
    }
    
    jmethodID getContextMethod = env->GetStaticMethodID(sdlActivityClass, "getContext",
        "()Landroid/content/Context;");
    if (getContextMethod == nullptr) {
        LOGE(LOG_TAG, "Failed to find getContext method");
        // 清理已分配的资源
        env->DeleteLocalRef(sdlActivityClass);
        env->DeleteLocalRef(serviceClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -7;
    }
    
    jobject context = env->CallStaticObjectMethod(sdlActivityClass, getContextMethod);
    if (context == nullptr) {
        LOGE(LOG_TAG, "Failed to get context");
        // 清理已分配的资源
        env->DeleteLocalRef(sdlActivityClass);
        env->DeleteLocalRef(serviceClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -5;
    }
    
    // 调用 launch
    LOGI(LOG_TAG, "Calling ProcessLauncherService.launch...");
    env->CallStaticVoidMethod(serviceClass, launchMethod,
        context, jAssemblyPath, jArgs, jStartupHooks, jTitle);
    
    // 清理所有 JNI 本地引用
    env->DeleteLocalRef(context);
    env->DeleteLocalRef(sdlActivityClass);
    env->DeleteLocalRef(serviceClass);
    env->DeleteLocalRef(jAssemblyPath);
    if (jArgs) env->DeleteLocalRef(jArgs);
    if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
    env->DeleteLocalRef(jTitle);
    
    LOGI(LOG_TAG, "Process launch requested!");
    LOGI(LOG_TAG, "========================================");
    return 0;
}