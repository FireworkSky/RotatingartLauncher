/*
 * SDL_android_main.c
 * 
 * CoreCLR 直接启动实现 - 完善程序集查找逻辑
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <pthread.h>
#include <dlfcn.h>
#include <stdio.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>

#define LOG_TAG "GameLauncher"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// CoreCLR 函数指针类型定义
typedef int (*coreclr_initialize_ptr)(
        const char* exePath,
        const char* appDomainFriendlyName,
        int propertyCount,
        const char** propertyKeys,
        const char** propertyValues,
        void** hostHandle,
        unsigned int* domainId);

typedef int (*coreclr_execute_assembly_ptr)(
        void* hostHandle,
        unsigned int domainId,
        int argc,
        const char** argv,
        const char* managedAssemblyPath,
        unsigned int* exitCode);

typedef int (*coreclr_shutdown_ptr)(
        void* hostHandle,
        unsigned int domainId);

// 全局变量存储参数
static char* g_appPath = NULL;
static char* g_dotnetPath = NULL;

// 全局JavaVM指针
static JavaVM* g_jvm = NULL;
static int g_threadAttached = 0;

// 文件系统辅助函数
int directory_exists(const char* path) {
    struct stat statbuf;
    return (stat(path, &statbuf) == 0 && S_ISDIR(statbuf.st_mode));
}

int file_exists(const char* path) {
    struct stat statbuf;
    return (stat(path, &statbuf) == 0 && S_ISREG(statbuf.st_mode));
}

// 递归扫描目录中的DLL文件
void scan_directory_for_dlls(const char* directory, char* result, size_t result_size, int recursive) {
    DIR* dir;
    struct dirent* entry;

    if ((dir = opendir(directory)) == NULL) {
        return;
    }

    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }

        char full_path[1024];
        snprintf(full_path, sizeof(full_path), "%s/%s", directory, entry->d_name);

        // 检查是否为DLL文件
        if (strlen(entry->d_name) > 4 &&
            strcmp(entry->d_name + strlen(entry->d_name) - 4, ".dll") == 0) {
            if (file_exists(full_path)) {
                if (strlen(result) > 0) {
                    strncat(result, ":", result_size - strlen(result) - 1);
                }
                strncat(result, full_path, result_size - strlen(result) - 1);
                LOGI("Found DLL: %s", full_path);
            }
        }

        // 如果是目录且需要递归扫描
        if (recursive && directory_exists(full_path)) {
            scan_directory_for_dlls(full_path, result, result_size, recursive);
        }
    }

    closedir(dir);
}

// 构建可信程序集列表 - 基于C#逻辑
char* build_trusted_assemblies_list(const char* appPath, const char* dotnetPath) {
    LOGI("=== Building Trusted Assemblies List ===");

    // 分配足够大的缓冲区
    size_t buffer_size = 65536; // 64KB 应该足够
    char* trusted_assemblies = (char*)calloc(buffer_size, 1);
    if (!trusted_assemblies) {
        LOGE("Failed to allocate memory for trusted assemblies list");
        return NULL;
    }

    // 提取应用程序目录
    char appDir[512];
    snprintf(appDir, sizeof(appDir), "%s", appPath);
    char* lastSlash = strrchr(appDir, '/');
    if (lastSlash) *lastSlash = '\0';

    LOGI("Application directory: %s", appDir);
    LOGI("Dotnet directory: %s", dotnetPath);

    // 1. 递归扫描应用程序目录中的所有DLL文件
    if (directory_exists(appDir)) {
        LOGI("Scanning application directory recursively...");
        scan_directory_for_dlls(appDir, trusted_assemblies, buffer_size, 1);
    } else {
        LOGI("WARNING: Application directory not found: %s", appDir);
    }

    // 2. 添加.NET Core框架程序集（不递归，框架目录是扁平结构）
    char frameworkPath[512];
    snprintf(frameworkPath, sizeof(frameworkPath), "%s/shared/Microsoft.NETCore.App/8.0.18", dotnetPath);

    if (directory_exists(frameworkPath)) {
        LOGI("Scanning framework directory (non-recursive)...");

        // 关键的核心程序集列表
        const char* core_assemblies[] = {
                "System.Private.CoreLib.dll",
                "System.Runtime.dll",
                "System.Runtime.Extensions.dll",
                "System.Console.dll",
                "System.IO.dll",
                "System.IO.FileSystem.dll",
                "System.Linq.dll",
                "System.Collections.dll",
                "System.Threading.dll",
                "System.Threading.Tasks.dll",
                "System.Text.RegularExpressions.dll",
                "System.Threading.Thread.dll",
                "netstandard.dll",
                "mscorlib.dll",
                "System.Memory.dll",
                "System.Buffers.dll",
                "System.Numerics.Vectors.dll",
                "System.Text.Encoding.Extensions.dll",
                "System.Reflection.dll",
                "System.Reflection.Extensions.dll",
                "System.Reflection.Primitives.dll",
                "System.Resources.ResourceManager.dll",
                "System.Runtime.InteropServices.dll",
                "System.Runtime.Loader.dll",
                "System.Runtime.Serialization.Primitives.dll",
                "System.Xml.ReaderWriter.dll",
                "System.Diagnostics.Debug.dll",
                "System.Diagnostics.Tools.dll",
                "System.Globalization.dll",
                "System.Globalization.Extensions.dll",
                NULL
        };

        // 添加所有存在的核心程序集
        for (int i = 0; core_assemblies[i] != NULL; i++) {
            char full_path[512];
            snprintf(full_path, sizeof(full_path), "%s/%s", frameworkPath, core_assemblies[i]);

            if (file_exists(full_path)) {
                if (strlen(trusted_assemblies) > 0) {
                    strncat(trusted_assemblies, ":", buffer_size - strlen(trusted_assemblies) - 1);
                }
                strncat(trusted_assemblies, full_path, buffer_size - strlen(trusted_assemblies) - 1);
                LOGI("Added framework DLL: %s", core_assemblies[i]);
            } else {
                LOGI("WARNING: Framework DLL not found: %s", full_path);
            }
        }

        // 扫描框架目录中的所有DLL（非递归）
        DIR* dir = opendir(frameworkPath);
        if (dir) {
            struct dirent* entry;
            while ((entry = readdir(dir)) != NULL) {
                // 只添加DLL文件
                if (strlen(entry->d_name) > 4 &&
                    strcmp(entry->d_name + strlen(entry->d_name) - 4, ".dll") == 0) {

                    // 检查是否已经在核心列表中
                    int found = 0;
                    for (int i = 0; core_assemblies[i] != NULL; i++) {
                        if (strcmp(entry->d_name, core_assemblies[i]) == 0) {
                            found = 1;
                            break;
                        }
                    }

                    // 如果不在核心列表中，则添加
                    if (!found) {
                        char full_path[512];
                        snprintf(full_path, sizeof(full_path), "%s/%s", frameworkPath, entry->d_name);

                        if (file_exists(full_path)) {
                            if (strlen(trusted_assemblies) > 0) {
                                strncat(trusted_assemblies, ":", buffer_size - strlen(trusted_assemblies) - 1);
                            }
                            strncat(trusted_assemblies, full_path, buffer_size - strlen(trusted_assemblies) - 1);
                            LOGI("Added additional framework DLL: %s", entry->d_name);
                        }
                    }
                }
            }
            closedir(dir);
        }
    } else {
        LOGI("WARNING: Framework directory not found: %s", frameworkPath);
    }

    // 3. 扫描额外的依赖目录（递归）
    const char* additional_dirs[] = {
            "/publish",
            "/libs",
            "/native",
            "/runtimes",
            NULL
    };

    for (int i = 0; additional_dirs[i] != NULL; i++) {
        char full_dir_path[512];

        // 在应用程序目录中查找
        snprintf(full_dir_path, sizeof(full_dir_path), "%s%s", appDir, additional_dirs[i]);
        if (directory_exists(full_dir_path)) {
            LOGI("Scanning additional directory recursively: %s", full_dir_path);
            scan_directory_for_dlls(full_dir_path, trusted_assemblies, buffer_size, 1);
        }

        // 在dotnet目录中查找
        snprintf(full_dir_path, sizeof(full_dir_path), "%s%s", dotnetPath, additional_dirs[i]);
        if (directory_exists(full_dir_path)) {
            LOGI("Scanning additional directory recursively: %s", full_dir_path);
            scan_directory_for_dlls(full_dir_path, trusted_assemblies, buffer_size, 1);
        }
    }

    // 4. 验证关键程序集是否存在
    LOGI("=== Verifying Critical Assemblies ===");
    const char* critical_assemblies[] = {
            "System.Private.CoreLib.dll",
            "System.Runtime.dll",
            "System.Linq.dll",
            "netstandard.dll",
            NULL
    };

    for (int i = 0; critical_assemblies[i] != NULL; i++) {
        int found = 0;
        char* pos = trusted_assemblies;

        while (pos && *pos) {
            char* next_colon = strchr(pos, ':');
            char path_segment[512];

            if (next_colon) {
                size_t len = next_colon - pos;
                strncpy(path_segment, pos, len);
                path_segment[len] = '\0';
                pos = next_colon + 1;
            } else {
                strcpy(path_segment, pos);
                pos = NULL;
            }

            char* filename = strrchr(path_segment, '/');
            if (filename && strcmp(filename + 1, critical_assemblies[i]) == 0) {
                found = 1;
                break;
            }
        }

        LOGI("Critical assembly %s found: %s", critical_assemblies[i], found ? "YES" : "NO");
    }

    LOGI("Trusted assemblies list built with %zu characters", strlen(trusted_assemblies));
    LOGI("Total trusted assemblies: approximately %zu",
         strlen(trusted_assemblies) > 0 ? 1 + strlen(trusted_assemblies) - strlen(trusted_assemblies) + 1 : 0);

    return trusted_assemblies;
}

// 构建原生DLL搜索路径
char* build_native_search_paths(const char* dotnetPath) {
    size_t buffer_size = 2048;
    char* search_paths = (char*)calloc(buffer_size, 1);
    if (!search_paths) {
        return NULL;
    }

    const char* paths[] = {
            dotnetPath,
            "/shared/Microsoft.NETCore.App/8.0.18",
            NULL
    };

    for (int i = 0; paths[i] != NULL; i++) {
        char full_path[512];

        // 如果是相对路径，添加到dotnet路径
        if (paths[i][0] == '/') {
            snprintf(full_path, sizeof(full_path), "%s%s", dotnetPath, paths[i]);
        } else {
            snprintf(full_path, sizeof(full_path), "%s", paths[i]);
        }

        if (directory_exists(full_path)) {
            if (strlen(search_paths) > 0) {
                strncat(search_paths, ":", buffer_size - strlen(search_paths) - 1);
            }
            strncat(search_paths, full_path, buffer_size - strlen(search_paths) - 1);
        }
    }

    return search_paths;
}

// JNI_OnLoad函数
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnLoad called");
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEnv* GetJNIEnv() {
    if (g_jvm == NULL) {
        LOGE("JavaVM is NULL in GetJNIEnv");
        return NULL;
    }

    JNIEnv* env = NULL;
    jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);

    if (result == JNI_EDETACHED) {
        LOGI("Current thread not attached, attaching now...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("Failed to attach current thread to JVM");
            return NULL;
        }
        g_threadAttached = 1;
        LOGI("Thread attached successfully");
    } else if (result != JNI_OK) {
        LOGE("Failed to get JNIEnv, error code: %d", result);
        return NULL;
    }

    return env;
}

void SafeDetachJNIEnv() {
    if (g_jvm != NULL && g_threadAttached) {
        JNIEnv* env = NULL;
        jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);

        if (result == JNI_OK) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
            g_threadAttached = 0;
            LOGI("Thread safely detached from JVM");
        } else {
            LOGI("Thread already detached or not attached");
        }
    }
}

// 清理全局内存
void CleanupGlobalMemory() {
    free(g_appPath);
    free(g_dotnetPath);
    g_appPath = g_dotnetPath = NULL;
}

// 设置启动参数的JNI方法
JNIEXPORT void JNICALL
Java_com_app_ralaunch_game_GameLauncher_setLaunchParams(JNIEnv *env, jclass clazz,
                                                        jstring appPath, jstring dotnetPath) {
    // 释放之前的内存
    CleanupGlobalMemory();

    // 将Java字符串转换为C字符串并复制到全局变量
    const char *app_path = (*env)->GetStringUTFChars(env, appPath, 0);
    const char *dotnet_path = (*env)->GetStringUTFChars(env, dotnetPath, 0);

    g_appPath = strdup(app_path);
    g_dotnetPath = strdup(dotnet_path);

    LOGI("Launch params set: appPath=%s, dotnetPath=%s", g_appPath, g_dotnetPath);

    (*env)->ReleaseStringUTFChars(env, appPath, app_path);
    (*env)->ReleaseStringUTFChars(env, dotnetPath, dotnet_path);
}

// CoreCLR 启动函数
int launch_with_coreclr(const char* appPath, const char* dotnetPath) {
    LOGI("=== Launching with CoreCLR API ===");
    LOGI("Assembly: %s", appPath);
    LOGI("Dotnet: %s", dotnetPath);

    char appDir[512];
    snprintf(appDir, sizeof(appDir), "%s", appPath);
    char* lastSlash = strrchr(appDir, '/');
    if (lastSlash) *lastSlash = '\0';

    LOGI("Setting current directory to: %s", appDir);
    if (chdir(appDir) != 0) {
        LOGE("Failed to set current directory: %s", strerror(errno));
        // 不返回错误，继续执行，但记录警告
        LOGI("WARNING: Current directory not set, file operations may fail");
    } else {
        LOGI("Current directory set successfully");

        // 验证当前目录
        char cwd[512];
        if (getcwd(cwd, sizeof(cwd)) != NULL) {
            LOGI("Current working directory: %s", cwd);
        }
    }

    // 构建 CoreCLR 库路径
    char coreclrPath[512];
    snprintf(coreclrPath, sizeof(coreclrPath), "%s/shared/Microsoft.NETCore.App/8.0.18/libcoreclr.so", dotnetPath);
    LOGI("CoreCLR library path: %s", coreclrPath);

    // 设置环境变量
    setenv("DOTNET_ROOT", dotnetPath, 1);
    setenv("COMPlus_EnableDiagnostics", "1", 1);
    setenv("COMPlus_LogEnable", "1", 1);

    // 添加忽略版本检查的环境变量
    setenv("TRUSTED_PLATFORM_ASSEMBLIES_IGNORE_VERSION", "1", 1);
    setenv("TRUSTED_PLATFORM_ASSEMBLIES_IGNORE_STRONG_NAME", "1", 1);
    setenv("TRUSTED_PLATFORM_ASSEMBLIES_ALLOW_PARTIAL", "1", 1);
    setenv("FX_OVERRIDE", "1", 1);

    LOGI("Environment variables set");

    // 加载 CoreCLR 库
    void* coreclrLib = dlopen(coreclrPath, RTLD_LAZY | RTLD_LOCAL);
    if (!coreclrLib) {
        LOGE("Failed to load CoreCLR: %s", dlerror());
        return -1;
    }
    LOGI("CoreCLR library loaded successfully");

    // 获取 CoreCLR 函数指针
    coreclr_initialize_ptr coreclr_initialize =
            (coreclr_initialize_ptr)dlsym(coreclrLib, "coreclr_initialize");
    coreclr_execute_assembly_ptr coreclr_execute_assembly =
            (coreclr_execute_assembly_ptr)dlsym(coreclrLib, "coreclr_execute_assembly");
    coreclr_shutdown_ptr coreclr_shutdown =
            (coreclr_shutdown_ptr)dlsym(coreclrLib, "coreclr_shutdown");

    if (!coreclr_initialize || !coreclr_execute_assembly || !coreclr_shutdown) {
        LOGE("Failed to get CoreCLR functions: %s", dlerror());
        dlclose(coreclrLib);
        return -1;
    }
    LOGI("CoreCLR function pointers obtained");

    // 构建可信程序集列表 - 使用新的完善逻辑
    char* trustedAssemblies = build_trusted_assemblies_list(appPath, dotnetPath);
    if (!trustedAssemblies) {
        LOGE("Failed to build trusted assemblies list");
        dlclose(coreclrLib);
        return -1;
    }

    // 构建原生搜索路径
    char* nativeSearchPaths = build_native_search_paths(dotnetPath);
    if (!nativeSearchPaths) {
        LOGE("Failed to build native search paths");
        free(trustedAssemblies);
        dlclose(coreclrLib);
        return -1;
    }

    // 注意：appDir 已经在函数开头提取过了，这里不需要重复提取

    // 设置 CoreCLR 属性
    const char* propertyKeys[] = {
            "TRUSTED_PLATFORM_ASSEMBLIES",
            "APP_PATHS",
            "APP_CONTEXT_BASE_DIRECTORY",
            "NATIVE_DLL_SEARCH_DIRECTORIES",
            "System.GC.Server",
            "System.Globalization.Invariant",
            "RUNTIME_IDENTIFIER"
    };

    const char* propertyValues[] = {
            trustedAssemblies,
            appDir,
            appDir,
            nativeSearchPaths,
            "false",
            "false",
            "linux-arm64"  // 根据实际情况调整
    };

    int propertyCount = sizeof(propertyKeys) / sizeof(propertyKeys[0]);

    LOGI("=== CoreCLR Properties ===");
    for (int i = 0; i < propertyCount; i++) {
        LOGI("  %s = %s", propertyKeys[i], propertyValues[i]);
    }

    // 初始化 CoreCLR
    void* hostHandle;
    unsigned int domainId;

    LOGI("Initializing CoreCLR...");
    int rc = coreclr_initialize(
            appPath,
            "AndroidAppDomain",
            propertyCount,
            propertyKeys,
            propertyValues,
            &hostHandle,
            &domainId
    );

    if (rc != 0) {
        LOGE("coreclr_initialize failed with code: 0x%08X", rc);
        free(trustedAssemblies);
        free(nativeSearchPaths);
        dlclose(coreclrLib);
        return -1;
    }

    LOGI("CoreCLR initialized successfully (handle: %p, domain: %u)", hostHandle, domainId);

    // 执行程序集
    unsigned int exitCode = 0;
    const char* argv_managed[] = { appPath };

    LOGI("Executing managed assembly...");
    rc = coreclr_execute_assembly(
            hostHandle,
            domainId,
            1,
            argv_managed,
            appPath,
            &exitCode
    );

    if (rc != 0) {
        LOGE("coreclr_execute_assembly failed with code: 0x%08X", rc);
    } else {
        LOGI("Managed assembly executed successfully (exit code: %u)", exitCode);
    }

    // 关闭 CoreCLR
    LOGI("Shutting down CoreCLR...");
    coreclr_shutdown(hostHandle, domainId);

    // 清理资源
    free(trustedAssemblies);
    free(nativeSearchPaths);
    dlclose(coreclrLib);

    LOGI("CoreCLR shutdown complete");

    return (rc == 0) ? (int)exitCode : -1;
}

// SDL_main 入口点
__attribute__ ((visibility("default"))) int SDL_main(int argc, char* argv[]) {
    LOGI("SDL_main started (CoreCLR mode)");

    // 检查必要参数
    if (g_appPath == NULL || g_dotnetPath == NULL) {
        LOGE("Launch parameters not set. Call setLaunchParams first!");
        return -1;
    }

    LOGI("Starting with parameters:");
    LOGI("  appPath: %s", g_appPath);
    LOGI("  dotnetPath: %s", g_dotnetPath);

    // 使用 CoreCLR 启动
    int result = launch_with_coreclr(g_appPath, g_dotnetPath);

    LOGI("CoreCLR execution finished with result: %d", result);

    // 获取JNIEnv
    JNIEnv* env = GetJNIEnv();
    if (env != NULL) {
        // 找到GameActivity类
        jclass clazz = (*env)->FindClass(env, "com/app/ralaunch/activity/GameActivity");
        if (clazz != NULL) {
            // 找到onGameExit静态方法
            jmethodID method = (*env)->GetStaticMethodID(env, clazz, "onGameExit", "(I)V");
            if (method != NULL) {
                // 调用静态方法
                (*env)->CallStaticVoidMethod(env, clazz, method, result);
            } else {
                LOGE("Failed to find method onGameExit");
            }
            // 删除局部引用
            (*env)->DeleteLocalRef(env, clazz);
        } else {
            LOGE("Failed to find class com/app/ralaunch/activity/GameActivity");
        }
    } else {
        LOGE("Failed to get JNIEnv in SDL_main");
    }

    // 清理资源
    CleanupGlobalMemory();
    SafeDetachJNIEnv();

    LOGI("SDL_main finished");
    return result;
}

// JNI_OnUnload函数
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnUnload called");
    // 清理资源
    CleanupGlobalMemory();
    g_jvm = NULL;
}