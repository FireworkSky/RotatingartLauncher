/**
 * @file dotnet_host.c
 * @brief .NET CoreCLR 宿主启动器实现
 * 
 * 参考 .NET Runtime corehost 实现增强错误处理
 */

#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <jni.h>
#include <android/log.h>
#include <signal.h>
#include <ucontext.h>
#include <sys/stat.h>
#include <errno.h>
#include "dotnet_params.h"
#include "jni_bridge.h"

#define LOG_TAG "GameLauncher"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// CoreCLR 错误码定义（参考 corehost）
#define CORECLR_E_INVALID_OPERATION     0x80131509
#define CORECLR_E_FILE_NOT_FOUND        0x80070002
#define CORECLR_E_TYPE_LOAD             0x80131522
#define CORECLR_E_METHOD_NOT_FOUND      0x80131513
#define CORECLR_E_ASSEMBLY_NOT_FOUND    0x80131040

/** 主程序集路径 */
char* h_appPath = NULL;

/** .NET 运行时路径（可选） */
char* h_dotnetPath = NULL;

/** 应用程序目录 */
char* h_appDir = NULL;

/** 受信程序集列表 */
char* g_trustedAssemblies = NULL;

/** 原生库搜索路径 */
char* g_nativeSearchPaths = NULL;

/** 启动器 DLL 路径 */
char* g_launcherDll = NULL;

/** 外部声明（定义在 dotnet_params.c） */
extern char* g_bootstrapDll;
extern char* g_targetGameAssembly;

/** CoreCLR 全局句柄（供回调使用） */
static void* g_coreclr_hostHandle = NULL;
static unsigned int g_coreclr_domainId = 0;
static void* g_coreclr_execute_assembly_fn = NULL;

/**
 * @brief 将 CoreCLR 错误码转换为可读的错误消息
 * 参考 corehost 的错误处理
 */
static const char* get_coreclr_error_message(int error_code) {
    switch (error_code) {
        case 0: return "Success";
        case 0x80004001: return "Not implemented";
        case 0x80004002: return "No such interface supported";
        case 0x80004005: return "Unspecified error";
        case 0x80070002: return "File not found";
        case 0x80070057: return "Invalid argument";
        case 0x8007000E: return "Out of memory";
        case 0x80131040: return "Assembly not found";
        case 0x80131509: return "Invalid operation";
        case 0x80131513: return "Method not found";
        case 0x80131522: return "Type load exception";
        case 0x80131700: return "IO exception";
        default: return "Unknown error";
    }
}

/**
 * @brief 验证关键文件是否存在
 * 参考 corehost 的依赖检查
 */
static int validate_runtime_dependencies(const char* runtime_dir) {
    LOGI("🔍 Validating runtime dependencies in: %s", runtime_dir);
    
    // 检查关键的 CoreCLR 文件
    const char* required_files[] = {
        "libcoreclr.so",
        "System.Private.CoreLib.dll",
        NULL
    };
    
    char file_path[2048];
    int missing_count = 0;
    
    for (int i = 0; required_files[i] != NULL; i++) {
        snprintf(file_path, sizeof(file_path), "%s/%s", runtime_dir, required_files[i]);
        
        struct stat st;
        if (stat(file_path, &st) != 0) {
            LOGE("❌ Missing required file: %s (errno: %d, %s)", 
                 required_files[i], errno, strerror(errno));
            missing_count++;
        } else {
            LOGI("✅ Found: %s (%ld bytes)", required_files[i], st.st_size);
        }
    }
    
    if (missing_count > 0) {
        LOGE("❌ Missing %d required runtime file(s)", missing_count);
        return 0;
    }
    
    LOGI("✅ All required runtime files present");
    return 1;
}

/**
 * @brief 验证 TPA (Trusted Platform Assemblies) 列表
 */
static void validate_tpa_list(const char* tpa) {
    if (!tpa || strlen(tpa) == 0) {
        LOGE("❌ TPA list is empty!");
        return;
    }
    
    int count = 1;
    for (const char* p = tpa; *p; p++) {
        if (*p == ':') count++;
    }
    
    LOGI("📦 TPA contains %d assemblies", count);
    
    // 检查关键程序集
    const char* critical_assemblies[] = {
        "System.Private.CoreLib.dll",
        "System.Runtime.dll",
        NULL
    };
    
    for (int i = 0; critical_assemblies[i] != NULL; i++) {
        if (strstr(tpa, critical_assemblies[i]) == NULL) {
            LOGW("⚠️  Critical assembly not in TPA: %s", critical_assemblies[i]);
        } else {
            LOGI("✅ Found critical assembly: %s", critical_assemblies[i]);
        }
    }
}

/**
 * @brief Native回调函数：供C#调用coreclr_execute_assembly
 * 
 * @param assemblyPath 要执行的程序集路径
 * @return 执行结果码
 */
static int native_execute_assembly_callback(const char* assemblyPath) {
    LOGI("[Native Callback] executeAssembly called for: %s", assemblyPath);
    LOGI("[Native Callback] g_coreclr_hostHandle = %p", g_coreclr_hostHandle);
    LOGI("[Native Callback] g_coreclr_domainId = %u", g_coreclr_domainId);
    LOGI("[Native Callback] g_coreclr_execute_assembly_fn = %p", g_coreclr_execute_assembly_fn);
    
    // 在Android/Mono上，hostHandle可能为NULL，但只要有execute_assembly_fn就可以继续
    if (!g_coreclr_execute_assembly_fn) {
        LOGE("[Native Callback] ERROR: execute_assembly_fn is NULL!");
        return -1;
    }
    
    if (!g_coreclr_hostHandle) {
        LOGI("[Native Callback] hostHandle is NULL (normal on Android/Mono)");
    }
    
    // 定义函数指针类型
    typedef int (*coreclr_execute_assembly_ptr)(void*,unsigned int,int,const char**,const char*,unsigned int*);
    
    unsigned int gameExitCode = 0;
    const char* args[] = { assemblyPath };
    
    coreclr_execute_assembly_ptr exec_fn = (coreclr_execute_assembly_ptr)g_coreclr_execute_assembly_fn;
    int result = exec_fn(
        g_coreclr_hostHandle,
        g_coreclr_domainId,
        1,
        args,
        assemblyPath,
        &gameExitCode
    );
    
    LOGI("[Native Callback] coreclr_execute_assembly returned: %d, exitCode: %u", result, gameExitCode);
    return result == 0 ? (int)gameExitCode : result;
}

/**
 * @brief 信号处理函数：捕获崩溃信号并记录详细信息
 * 
 * @param sig 信号编号
 * @param si 信号信息结构
 * @param context 上下文信息（包含寄存器状态等）
 * 
 * 此函数会在程序收到致命信号（SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL）时被调用，
 * 记录崩溃时的详细信息，包括信号类型、故障地址、寄存器状态等。
 */
static void signal_handler(int sig, siginfo_t *si, void *context) {
    LOGE("=====================================================");
    LOGE("🔴 FATAL SIGNAL CAUGHT: %d", sig);
    LOGE("=====================================================");
    
    // 记录信号类型
    const char* sig_name = "UNKNOWN";
    switch(sig) {
        case SIGSEGV: sig_name = "SIGSEGV (Segmentation Fault)"; break;
        case SIGABRT: sig_name = "SIGABRT (Abort)"; break;
        case SIGBUS: sig_name = "SIGBUS (Bus Error)"; break;
        case SIGFPE: sig_name = "SIGFPE (Floating Point Exception)"; break;
        case SIGILL: sig_name = "SIGILL (Illegal Instruction)"; break;
    }
    LOGE("Signal: %s", sig_name);
    
    // 记录故障地址
    if (si) {
        LOGE("Fault address: %p", si->si_addr);
        LOGE("Signal code: %d", si->si_code);
        
        // SIGSEGV 详细代码
        if (sig == SIGSEGV) {
            const char* segv_reason = "UNKNOWN";
            switch(si->si_code) {
                case SEGV_MAPERR: segv_reason = "Address not mapped"; break;
                case SEGV_ACCERR: segv_reason = "Invalid permissions"; break;
            }
            LOGE("SIGSEGV Reason: %s", segv_reason);
        }
    }
    
    // Android NDK 不支持 backtrace，跳过堆栈回溯
    // 详细的堆栈信息会在 tombstone 文件中生成
    LOGE("Stack trace will be available in tombstone file");
    
    // 记录寄存器状态（ARM64）
    if (context) {
        ucontext_t* uc = (ucontext_t*)context;
        LOGE("----- Register State (ARM64) -----");
        #ifdef __aarch64__
        mcontext_t* mc = &uc->uc_mcontext;
        LOGE("PC (程序计数器): %p", (void*)mc->pc);
        LOGE("SP (栈指针): %p", (void*)mc->sp);
        LOGE("X0: %016llx  X1: %016llx", mc->regs[0], mc->regs[1]);
        LOGE("X2: %016llx  X3: %016llx", mc->regs[2], mc->regs[3]);
        LOGE("X4: %016llx  X5: %016llx", mc->regs[4], mc->regs[5]);
        #endif
    }
    
    // 记录当前状态
    LOGE("----- Launch Parameters -----");
    LOGE("appPath: %s", h_appPath ? h_appPath : "(null)");
    LOGE("appDir: %s", h_appDir ? h_appDir : "(null)");
    LOGE("dotnetPath: %s", h_dotnetPath ? h_dotnetPath : "(null)");
    LOGE("launcherDll: %s", g_launcherDll ? g_launcherDll : "(null)");
    LOGE("nativeSearchPaths: %s", g_nativeSearchPaths ? g_nativeSearchPaths : "(null)");
    
    LOGE("=====================================================");
    LOGE("🔴 CRASH INFORMATION END - Calling default handler");
    LOGE("=====================================================");
    
    // 恢复默认处理并重新触发信号
    signal(sig, SIG_DFL);
    raise(sig);
}

/**
 * @brief 安装信号处理器
 * 
 * 为多个致命信号安装处理器，以便在崩溃时捕获并记录详细信息。
 */
static void install_signal_handlers() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = signal_handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    
    // 安装信号处理器
    sigaction(SIGSEGV, &sa, NULL);  // 段错误
    sigaction(SIGABRT, &sa, NULL);  // 中止
    sigaction(SIGBUS, &sa, NULL);   // 总线错误
    sigaction(SIGFPE, &sa, NULL);   // 浮点异常
    sigaction(SIGILL, &sa, NULL);   // 非法指令
    
    LOGI("✓ Signal handlers installed for crash detection");
}

/**
 * @brief JNI 函数：设置完整的启动参数
 * 
 * @param env JNI 环境指针
 * @param clazz Java 类引用
 * @param appPath 应用程序主程序集路径
 * @param dotnetPath .NET 运行时路径
 * @param appDir 应用程序目录
 * @param trustedAssemblies 受信程序集列表（: 分隔）
 * @param nativeSearchPaths 原生库搜索路径（: 分隔）
 * @param mainAssemblyPath 主程序集路径（启动器 DLL）
 * 
 * 此函数从 Java 层接收所有启动参数，并存储到全局变量中。
 * 旧的全局变量值会被自动释放。
 */
JNIEXPORT void JNICALL Java_com_app_ralaunch_game_GameLauncher_setLaunchParamsFull(
    JNIEnv* env, jclass clazz,
    jstring appPath, jstring dotnetPath, jstring appDir, jstring trustedAssemblies, jstring nativeSearchPaths, jstring mainAssemblyPath) {
    // 宏：释放旧值并赋予新值
    #define FREE_AND_ASSIGN(VAR, JS) do { if (VAR) free(VAR); if (JS) { const char* tmp = (*env)->GetStringUTFChars(env, JS, 0); VAR = strdup(tmp); (*env)->ReleaseStringUTFChars(env, JS, tmp); } else VAR = NULL; } while(0)
    FREE_AND_ASSIGN(h_appPath, appPath);
    FREE_AND_ASSIGN(h_dotnetPath, dotnetPath);
    FREE_AND_ASSIGN(h_appDir, appDir);
    FREE_AND_ASSIGN(g_trustedAssemblies, trustedAssemblies);
    FREE_AND_ASSIGN(g_nativeSearchPaths, nativeSearchPaths);
    FREE_AND_ASSIGN(g_launcherDll, mainAssemblyPath);
}

/**
 * @brief JNI 函数：兼容性包装（用于 NativeBridge 类）
 * 
 * 此函数提供了对新 Java 类名 NativeBridge 的兼容支持，
 * 内部直接调用 GameLauncher 的实现。
 */
JNIEXPORT void JNICALL Java_com_app_ralaunch_game_NativeBridge_setLaunchParamsFull(
    JNIEnv* env, jclass clazz,
    jstring appPath, jstring dotnetPath, jstring appDir, jstring trustedAssemblies, jstring nativeSearchPaths, jstring mainAssemblyPath) {
    Java_com_app_ralaunch_game_GameLauncher_setLaunchParamsFull(env, clazz, appPath, dotnetPath, appDir, trustedAssemblies, nativeSearchPaths, mainAssemblyPath);
}

/**
 * @brief 通过 CoreCLR 启动 .NET 应用程序
 * 
 * @return 应用程序退出码或错误码
 * 
 * 此函数是 .NET 应用程序启动的核心实现，执行以下步骤：
 * 1. 切换工作目录到应用程序目录
 * 2. 设置 LD_LIBRARY_PATH 环境变量
 * 3. 加载 CoreCLR 动态库（libcoreclr.so）
 * 4. 获取 CoreCLR API 函数指针
 * 5. 初始化 CoreCLR 运行时
 * 6. 执行主程序集
 * 7. 关闭运行时并清理资源
 */
int launch_with_coreclr_passthrough() {
    LOGI("launch_with_coreclr_passthrough: app=%s dir=%s launcher=%s", h_appPath, h_appDir, g_launcherDll);
    
    // 0. 安装信号处理器以捕获崩溃
    install_signal_handlers();
    
    // 1. 切换到应用程序目录
    chdir(h_appDir);
    
    // 2. 设置原生库搜索路径环境变量
    if (g_nativeSearchPaths && *g_nativeSearchPaths)
        setenv("LD_LIBRARY_PATH", g_nativeSearchPaths, 1);
    
    // 2.5 设置 CoreCLR 稳定性和调试环境变量
    // 尝试使用更保守的 GC 设置来避免多线程问题
    setenv("COMPlus_gcServer", "0", 1);              // 使用工作站 GC 而不是服务器 GC
    setenv("COMPlus_gcConcurrent", "0", 1);          // 禁用并发 GC
    setenv("COMPlus_ZapDisable", "1", 1);            // 禁用 ReadyToRun
    setenv("COMPlus_ReadyToRun", "0", 1);            // 禁用 ReadyToRun（备用）
    setenv("COMPlus_EnableEventLog", "0", 1);        // 禁用事件日志
    setenv("COMPlus_DefaultStackSize", "1000000", 1); // 增加默认栈大小（1MB）
    setenv("DOTNET_LegacyThreadingModel", "1", 1);   // 使用旧线程模型
    setenv("DOTNET_EnableWriteXorExecute", "0", 1);  // 禁用 W^X 以提高稳定性
    setenv("DOTNET_GCHeapCount", "1", 1);            // 强制单个 GC 堆
    setenv("DOTNET_GCGen0Size", "10000000", 1);      // 设置 Gen0 大小为 10MB
    setenv("DOTNET_DbgEnableMiniDump", "0", 1);      // 禁用 mini dumps
    setenv("COMPlus_Thread_UseAllCpuGroups", "0", 1); // 禁用多 CPU 组
    setenv("COMPlus_GCRetainVM", "1", 1);            // GC 保留 VM（避免重复初始化）
    setenv("COMPlus_JitMinOpts", "1", 1);            // 使用最小优化（提高稳定性）
    LOGI("CoreCLR GC and optimization settings configured for stability");
    
    // 设置详细日志环境变量（如果启用）
    LOGI("🔍 [DotnetHost] Checking g_verboseLogging: %d", g_verboseLogging);
    if (g_verboseLogging) {
        // CoreCLR 详细日志环境变量（输出到 logcat）
        setenv("COREHOST_TRACE", "1", 1);
        // ❌ 不使用文件输出，让日志直接输出到 stderr（会被 logcat 捕获）
        // setenv("COREHOST_TRACEFILE", "/data/local/tmp/corehost_trace.log", 1);
        
        // CoreCLR 运行时日志（会输出到 logcat）
        setenv("COMPlus_LogEnable", "1", 1);
        setenv("COMPlus_LogLevel", "10", 1);           // 最详细级别
        setenv("COMPlus_LogToConsole", "1", 1);        // 输出到控制台（stderr）
        setenv("COMPlus_LogFacility", "0xFFFFFFFF", 1); // 所有设施
        
        // CoreCLR 加载器和类型系统日志
        setenv("COMPlus_LogToDebugger", "1", 1);
        setenv("COMPlus_LogWithPid", "0", 1);
        setenv("COMPlus_LogFile", "", 1);              // 不输出到文件
        
        // CoreCLR GC 日志
        setenv("COMPlus_GCLogEnabled", "1", 1);
        setenv("COMPlus_GCLogFile", "", 1);            // GC 日志也输出到 stderr
        
        // 程序集加载日志
        setenv("COMPlus_LoaderOptimization", "0", 1);
        setenv("DOTNET_MULTILEVEL_LOOKUP", "0", 1);
        
        // ⚠️ JIT 反汇编日志会产生海量输出，已禁用
        // setenv("COMPlus_JitDisasm", "*", 1);
        // setenv("COMPlus_JitDump", "*", 1);
        
        // Mono 详细日志环境变量（兼容性）
        setenv("MONO_LOG_LEVEL", "debug", 1);
        setenv("MONO_LOG_MASK", "all", 1);
        setenv("XUNIT_VERBOSE", "true", 1);
        setenv("MONO_VERBOSE_METHOD", "1", 1);
        setenv("MONO_DEBUG", "1", 1);
        setenv("MONO_TRACE_ASSEMBLY", "1", 1);
        setenv("MONO_TRACE", "all", 1);
       
   
        LOGI("✓ Verbose logging ENABLED - CoreCLR/Mono logs will output to Logcat (tag: stderr)");
        LOGI("   Use 'adb logcat | grep -E \"CoreCLR|COREHOST|Mono\"' to filter runtime logs");
    } else {
        LOGI("Verbose logging disabled (use Settings to enable for debugging)");
    }
    
    // 设置 FNA 渲染器环境变量
    if (g_renderer) {
        if (strcmp(g_renderer, "opengles3") == 0) {
            // 使用原生 OpenGL ES 3（Android 原生支持，推荐）
            setenv("FNA3D_FORCE_DRIVER", "OpenGL", 1);
            setenv("FNA3D_OPENGL_FORCE_CORE_PROFILE", "0", 1);     // 禁用 Core Profile
            setenv("FNA3D_OPENGL_FORCE_ES3", "1", 1);              // 强制使用 ES3
            setenv("FNA3D_OPENGL_FORCE_VER_MAJOR", "3", 1);        // 限制 OpenGL 主版本为 3
            setenv("FNA3D_OPENGL_FORCE_VER_MINOR", "0", 1);        // 限制 OpenGL 次版本为 0
            setenv("FNA3D_OPENGL_FORCE_COMPATIBILITY_PROFILE", "1", 1);  // 强制兼容性模式
            
            // ⚠️ 关键：告诉 SDL 使用原生 GLES 渲染器（不是 gl4es）
            setenv("FNA3D_OPENGL_DRIVER", "native", 1);
            
            // SDL hints - 忽略 GL 扩展加载错误并禁用高级特性
            setenv("SDL_VIDEO_X11_FORCE_EGL", "1", 1);
            setenv("SDL_OPENGL_ES_DRIVER", "1", 1);
            setenv("SDL_VIDEO_GL_DRIVER", "", 1);
            
            // 禁用所有不支持的OpenGL扩展和高级特性
            setenv("FNA3D_DISABLE_ARB_DEBUG_OUTPUT", "1", 1);
            setenv("FNA3D_DISABLE_ARB_EXTENSION", "1", 1);
            setenv("FNA3D_FORCE_GL_ENABLE_DEBUG_OUTPUT", "0", 1);
            
            // 禁用着色器特化（Shader Specialization）- 这是导致glSpecializeShaderARB错误的原因
            setenv("FNA3D_DISABLE_SHADER_SPECIALIZATION", "1", 1);
            
            // 强制SDL忽略扩展加载失败
            setenv("SDL_HINT_VIDEO_ALLOW_SCREENSAVER", "1", 1);
            
            LOGI("✓ FNA renderer: Native OpenGL ES 3 (best performance)");
        } else if (strcmp(g_renderer, "opengl_gl4es") == 0) {
            // 使用 gl4es 作为 OpenGL 翻译层（Android AGL 接口方案）
            // 
            // ⚠️ 架构说明（基于 gl4es AGL 接口）：
            // 
            // Android 实现原理：
            // 1. gl4es 静态链接，提供 AGL 接口（不是 EGL）
            // 2. SDL 使用自定义的 OpenGL 后端（不是 EGL）
            // 3. AGL 接口函数：
            //    - aglCreateContext2：创建 OpenGL context
            //    - aglMakeCurrent：设置当前 context
            //    - aglSwapBuffers：交换缓冲区
            //    - aglGetProcAddress：获取 OpenGL 函数指针
            //    - aglDestroyContext：销毁 context
            // 4. gl4es 在 AGL 层内部管理 EGL/GLES
            // 
            // SDL 适配（app/src/main/cpp/SDL/src/video/android/SDL_androidgl4es.c）：
            // 1. SDL 编译时定义 SDL_VIDEO_OPENGL_GL4ES
            // 2. SDL 使用 Android_GL4ES_* 函数而不是标准 EGL 函数
            // 3. gl4es 的 AGL 接口在底层管理 EGL 和 GLES
            // 4. SDL 认为自己在使用 OpenGL（兼容性 profile）
            // 
            LOGI("🔧 Configuring OpenGL via gl4es AGL interface for Android...");
            
            // ⚠️ 关键：告诉 SDL 使用 gl4es 渲染器
            setenv("FNA3D_OPENGL_DRIVER", "gl4es", 1);
            
            // ⚠️ 关键：告诉 FNA3D 使用 gl4es（用于OpenGL兼容性profile）
            // FNA3D 会使用 OpenGL Compatibility Profile
            setenv("FNA3D_USE_GL4ES", "1", 1);
            
            // ⚠️ 关键：强制使用 OpenGL driver（不是 ES）
            setenv("FNA3D_FORCE_DRIVER", "OpenGL", 1);
            
            // SDL 已在编译时配置为使用 gl4es AGL 接口（SDL_VIDEO_OPENGL_GL4ES）
            // 无需设置 SDL_VIDEO_GL_DRIVER
            
            // gl4es 环境变量配置
            // LIBGL_ES: 目标 OpenGL ES 版本（2=GLES2, 3=GLES3）
            // LIBGL_GL: 模拟的桌面 OpenGL 版本（21=2.1, 30=3.0, etc）
            setenv("LIBGL_ES", "2", 1);      // 目标 GLES 2.0（兼容性最好）
            setenv("LIBGL_GL", "21", 1);     // 模拟 OpenGL 2.1
            setenv("LIBGL_LOGERR", "1", 1);  // 记录错误
            setenv("LIBGL_DEBUG", "1", 1);   // 调试信息
            
            // 设置工作目录到tModLoader目录
            if (h_appDir && strlen(h_appDir) > 0) {
                LOGI("🔧 Setting working directory to: %s", h_appDir);
                if (chdir(h_appDir) != 0) {
                    LOGW("⚠️ Failed to change working directory to %s", h_appDir);
                } else {
                    LOGI("✅ Working directory set successfully");
                }
            }
            
            LOGI("✓ FNA renderer: OpenGL + gl4es AGL (Android, static-linked)");
        } else if (strcmp(g_renderer, "vulkan") == 0) {
            // Vulkan 渲染器（实验性）
            setenv("FNA3D_FORCE_DRIVER", "Vulkan", 1);
            LOGI("✓ FNA renderer: Vulkan (experimental)");
        } else {
            LOGW("Unknown renderer type: %s, using default", g_renderer);
        }
    } else {
        // 默认使用原生 OpenGL ES 3
        setenv("FNA3D_FORCE_DRIVER", "OpenGL", 1);
        setenv("FNA3D_OPENGL_FORCE_CORE_PROFILE", "0", 1);
        setenv("FNA3D_OPENGL_FORCE_ES3", "1", 1);
        setenv("FNA3D_OPENGL_FORCE_VER_MAJOR", "3", 1);           // 限制版本
        setenv("FNA3D_OPENGL_FORCE_VER_MINOR", "0", 1);
        setenv("FNA3D_OPENGL_FORCE_COMPATIBILITY_PROFILE", "1", 1);
        // 禁用不支持的高级OpenGL扩展
        setenv("FNA3D_DISABLE_ARB_DEBUG_OUTPUT", "1", 1);
        setenv("FNA3D_DISABLE_ARB_EXTENSION", "1", 1);
        setenv("SDL_VIDEO_GL_DRIVER", "", 1);
        setenv("SDL_VIDEO_X11_FORCE_EGL", "1", 1);
        setenv("SDL_OPENGL_ES_DRIVER", "1", 1);
        LOGI("✓ FNA renderer: Native OpenGL ES 3 (default)");
    }

    // 3. 预加载并初始化 .NET 加密库（需要 JNI）
    // 注：gl4es现在使用Gish方案（静态链接），在JNI_OnLoad中已经初始化
    char firstPath[1024] = {0};
    if (g_nativeSearchPaths) {
        const char* sep = strchr(g_nativeSearchPaths, ':');
        size_t len = sep ? (size_t)(sep - g_nativeSearchPaths) : strlen(g_nativeSearchPaths);
        if (len >= sizeof(firstPath)) len = sizeof(firstPath) - 1;
        memcpy(firstPath, g_nativeSearchPaths, len);
        firstPath[len] = '\0';
    }
    
    if (firstPath[0] != '\0') {
        char cryptoPath[1536];
        snprintf(cryptoPath, sizeof(cryptoPath), "%s/libSystem.Security.Cryptography.Native.Android.so", firstPath);
        
        LOGI("🔐 Attempting to preload crypto library: %s", cryptoPath);
        void* cryptoLib = dlopen(cryptoPath, RTLD_NOW | RTLD_GLOBAL);
        if (cryptoLib) {
            LOGI("✓ Crypto library loaded successfully");
            
            // 查找并调用 JNI_OnLoad 函数来初始化加密库
            typedef jint (*JNI_OnLoad_t)(JavaVM*, void*);
            JNI_OnLoad_t crypto_onload = (JNI_OnLoad_t)dlsym(cryptoLib, "JNI_OnLoad");
            if (crypto_onload) {
                JavaVM* jvm = Bridge_GetJavaVM();
                if (jvm) {
                    jint jni_version = crypto_onload(jvm, NULL);
                    LOGI("✓ Crypto library JNI initialized (version: 0x%x)", jni_version);
                } else {
                    LOGW("⚠️  JavaVM not available, crypto JNI not initialized");
                }
            } else {
                LOGI("ℹ️  Crypto library has no JNI_OnLoad (may not need it)");
            }
        } else {
            LOGW("⚠️  Failed to preload crypto library: %s", dlerror());
            LOGI("ℹ️  Will try to load it lazily via CoreCLR");
        }
    }
    
    char coreclrPath[1536];
    if (firstPath[0] != '\0') 
        snprintf(coreclrPath, sizeof(coreclrPath), "%s/libcoreclr.so", firstPath);
    else 
        snprintf(coreclrPath, sizeof(coreclrPath), "libcoreclr.so");
    
    // 5. 加载 CoreCLR 动态库
    void* coreclrLib = dlopen(coreclrPath, RTLD_LAZY | RTLD_LOCAL);
    if (!coreclrLib) { 
        LOGE("dlopen coreclr.so fail: %s", dlerror()); 
        return -11; 
    }
    
    // 6. 定义 CoreCLR API 函数指针类型
    typedef int (*coreclr_initialize_ptr)(const char*,const char*,int,const char**,const char**,void**,unsigned int*);
    typedef int (*coreclr_execute_assembly_ptr)(void*,unsigned int,int,const char**,const char*,unsigned int*);
    typedef int (*coreclr_shutdown_ptr)(void*,unsigned int);
    typedef int (*coreclr_create_delegate_ptr)(void*,unsigned int,const char*,const char*,const char*,void**);
    
    // 7. 获取 CoreCLR API 函数指针
    dlerror(); // 清除之前的错误
    coreclr_initialize_ptr coreclr_initialize = (coreclr_initialize_ptr)dlsym(coreclrLib, "coreclr_initialize");
    const char* err1 = dlerror();
    if (err1) LOGE("dlsym coreclr_initialize fail: %s", err1);
    
    coreclr_execute_assembly_ptr coreclr_execute_assembly = (coreclr_execute_assembly_ptr)dlsym(coreclrLib, "coreclr_execute_assembly");
    const char* err2 = dlerror();
    if (err2) LOGE("dlsym coreclr_execute_assembly fail: %s", err2);
    
    coreclr_shutdown_ptr coreclr_shutdown = (coreclr_shutdown_ptr)dlsym(coreclrLib, "coreclr_shutdown");
    const char* err3 = dlerror();
    if (err3) {
        LOGW("dlsym coreclr_shutdown fail: %s (可能在 .NET 7+ 中已移除，将跳过)", err3);
    }
    
    coreclr_create_delegate_ptr coreclr_create_delegate = (coreclr_create_delegate_ptr)dlsym(coreclrLib, "coreclr_create_delegate");
    const char* err4 = dlerror();
    if (err4) LOGE("dlsym coreclr_create_delegate fail: %s", err4);
    
    // 注意: coreclr_shutdown 在 .NET 7+ 中可能不存在，这是正常的
    if (!coreclr_initialize || !coreclr_execute_assembly) { 
        dlclose(coreclrLib); 
        LOGE("coreclr dlsym fail: init=%p, exec=%p, shutdown=%p, delegate=%p", 
             coreclr_initialize, coreclr_execute_assembly, coreclr_shutdown, coreclr_create_delegate); 
        return -12; 
    }
    
    if (coreclr_shutdown) {
        LOGI("CoreCLR shutdown function available");
    } else {
        LOGW("CoreCLR shutdown function not available (expected in .NET 7+)");
    }
    
    // 保存coreclr_execute_assembly函数指针供回调使用
    g_coreclr_execute_assembly_fn = (void*)coreclr_execute_assembly;
    
    // 7. 准备 CoreCLR 初始化参数
    const char* keys[] = { 
        "TRUSTED_PLATFORM_ASSEMBLIES",      // 受信程序集列表
        "APP_PATHS",                        // 应用程序路径
        "APP_CONTEXT_BASE_DIRECTORY",       // 应用程序基础目录
        "NATIVE_DLL_SEARCH_DIRECTORIES"     // 原生 DLL 搜索目录
    };
    const char* vals[] = { 
        g_trustedAssemblies, 
        h_appDir, 
        h_appDir, 
        g_nativeSearchPaths 
    };
    
    // 7.5 初始化 JNI 环境（CoreCLR 在 Android 上需要 JNI）
    LOGI("Initializing JNI environment for CoreCLR...");
    JNIEnv* env = Bridge_GetJNIEnv();
    if (!env) {
        dlclose(coreclrLib);
        LOGE("Failed to get JNI environment");
        return -15;
    }
    LOGI("JNI environment initialized successfully at %p", env);
    
    // 8. 初始化 CoreCLR 运行时
    void* hostHandle; 
    unsigned int domainId;
    
    // === 预检查阶段 ===
    LOGI("========== CoreCLR Pre-Initialization Checks ==========");
    
    // 验证运行时依赖
    char runtime_dir[1536];
    if (g_nativeSearchPaths && strlen(g_nativeSearchPaths) > 0) {
        strncpy(runtime_dir, g_nativeSearchPaths, sizeof(runtime_dir) - 1);
        runtime_dir[sizeof(runtime_dir) - 1] = '\0';
        
        // 提取第一个路径
        char* colon = strchr(runtime_dir, ':');
        if (colon) *colon = '\0';
        
        if (!validate_runtime_dependencies(runtime_dir)) {
            LOGE("❌ Runtime dependency validation failed!");
            dlclose(coreclrLib);
            return -13;
        }
    } else {
        LOGW("⚠️  No native search paths specified, skipping dependency check");
    }
    
    // 验证 TPA 列表
    validate_tpa_list(g_trustedAssemblies);
    
    // 验证启动程序集存在
    struct stat st;
    if (stat(g_launcherDll, &st) != 0) {
        LOGE("❌ Launcher assembly not found: %s (errno: %d, %s)", 
             g_launcherDll, errno, strerror(errno));
        dlclose(coreclrLib);
        return -13;
    }
    LOGI("✅ Launcher assembly exists: %s (%ld bytes)", g_launcherDll, st.st_size);
    
    LOGI("=======================================================");
    
    // 打印初始化参数以便调试
    LOGI("========== CoreCLR Initialization Parameters ==========");
    LOGI("Executable Path: %s", g_launcherDll);
    LOGI("App Domain Name: AppDomain");
    LOGI("Property Count: 4");
    for (int i = 0; i < 4; i++) {
        LOGI("  [%d] %s = %s", i, keys[i], vals[i] ? (strlen(vals[i]) > 200 ? "[too long to display]" : vals[i]) : "[NULL]");
    }
    LOGI("=======================================================");
    
    LOGI(">>> About to call coreclr_initialize...");
    int rc = coreclr_initialize(g_launcherDll, "AppDomain", 4, keys, vals, &hostHandle, &domainId);
    LOGI("<<< coreclr_initialize returned: 0x%08X (%d)", rc, rc);
    LOGI("    hostHandle = %p (from coreclr_initialize)", hostHandle);
    LOGI("    domainId = %u (from coreclr_initialize)", domainId);
    
    if (rc != 0) {
        const char* error_msg = get_coreclr_error_message(rc);
        LOGE("❌ coreclr_initialize FAILED!");
        LOGE("   Error Code: 0x%08X (%d)", rc, rc);
        LOGE("   Error Message: %s", error_msg);
        LOGE("   Executable: %s", g_launcherDll);
        LOGE("   App Domain: AppDomain");
        
        // 打印可能的原因
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("Possible causes:");
        LOGE("  1. Missing or corrupted runtime files");
        LOGE("  2. Invalid TPA (Trusted Platform Assemblies) list");
        LOGE("  3. Incompatible .NET runtime version");
        LOGE("  4. Missing System.Private.CoreLib.dll");
        LOGE("  5. Insufficient memory or permissions");
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        dlclose(coreclrLib); 
        return -13; 
    }
    
    LOGI("✅ CoreCLR initialized successfully!");
    
    // 注意：在Android/Mono上，hostHandle和domainId可能都是0，但只要rc==0就表示成功
    // 我们信任返回值，而不是检查句柄是否为NULL
    if (!hostHandle || domainId == 0) {
        LOGI("ℹ️  hostHandle=%p, domainId=%u (may be 0 on Android/Mono, this is normal)", hostHandle, domainId);
    }
    
    // 9. Bootstrap模式：通过coreclr_create_delegate调用托管方法
    unsigned int exitCode = 0;
    
    if (g_bootstrapDll && g_targetGameAssembly) {
        LOGI("🚀 Using Bootstrap mode via coreclr_create_delegate");
        LOGI("   Bootstrap: %s", g_bootstrapDll);
        LOGI("   Target Game: %s", g_targetGameAssembly);
        
        // 定义委托类型（匹配UnmanagedCallersOnly签名）
        typedef void (*SetCallbackDelegate)(void* callbackPtr);
        typedef int (*LaunchGameDelegate)(const char* targetGamePathPtr);
        
        SetCallbackDelegate setCallbackFunc = NULL;
        LaunchGameDelegate launchGameFunc = NULL;
        
        // 1. 创建SetExecuteAssemblyCallback委托
        rc = coreclr_create_delegate(
            hostHandle,
            domainId,
            "Bootstrap",
            "AssemblyMain.Program",
            "SetExecuteAssemblyCallback",
            (void**)&setCallbackFunc
        );
        
        if (rc != 0) {
            const char* error_msg = get_coreclr_error_message(rc);
            LOGE("❌ Failed to create SetExecuteAssemblyCallback delegate!");
            LOGE("   Error Code: 0x%08X (%d)", rc, rc);
            LOGE("   Error Message: %s", error_msg);
            LOGE("   Assembly: Bootstrap");
            LOGE("   Type: AssemblyMain.Program");
            LOGE("   Method: SetExecuteAssemblyCallback");
            LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            LOGE("Possible causes:");
            LOGE("  1. Bootstrap.dll not in TPA list");
            LOGE("  2. Incorrect class/method name");
            LOGE("  3. Method signature mismatch");
            LOGE("  4. Assembly version incompatibility");
            LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            dlclose(coreclrLib);
            return -14;
        }
        
        LOGI("✅ SetExecuteAssemblyCallback delegate created successfully");
        
        // 2. 保存CoreCLR句柄供回调使用
        g_coreclr_hostHandle = hostHandle;
        g_coreclr_domainId = domainId;
        LOGI("✅ CoreCLR handles saved: hostHandle=%p, domainId=%u, execute_fn=%p", 
             g_coreclr_hostHandle, g_coreclr_domainId, g_coreclr_execute_assembly_fn);
        
        // 3. 将回调函数指针传递给C#（使用全局函数native_execute_assembly_callback）
        LOGI("Setting executeAssembly callback...");
        setCallbackFunc((void*)native_execute_assembly_callback);
        
        // 4. 创建LaunchGame委托
        rc = coreclr_create_delegate(
            hostHandle,
            domainId,
            "Bootstrap",
            "AssemblyMain.Program",
            "LaunchGame",
            (void**)&launchGameFunc
        );
        
        if (rc != 0) {
            const char* error_msg = get_coreclr_error_message(rc);
            LOGE("❌ Failed to create LaunchGame delegate!");
            LOGE("   Error Code: 0x%08X (%d)", rc, rc);
            LOGE("   Error Message: %s", error_msg);
            LOGE("   Assembly: Bootstrap");
            LOGE("   Type: AssemblyMain.Program");
            LOGE("   Method: LaunchGame");
            LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            LOGE("Possible causes:");
            LOGE("  1. Bootstrap.dll not in TPA list");
            LOGE("  2. Incorrect class/method name");
            LOGE("  3. Method signature mismatch (should accept string)");
            LOGE("  4. Method not marked with [UnmanagedCallersOnly]");
            LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            dlclose(coreclrLib);
            return -15;
        }
        
        LOGI("✅ LaunchGame delegate created successfully");
        
        // 5. 调用Bootstrap.LaunchGame
        LOGI("🎮 Calling Bootstrap.LaunchGame(\"%s\")", g_targetGameAssembly);
        LOGI("LaunchGame function pointer: %p", launchGameFunc);
        LOGI("Target game assembly pointer: %p", g_targetGameAssembly);
        LOGI("Target game assembly string: \"%s\"", g_targetGameAssembly);
        
        int result = launchGameFunc(g_targetGameAssembly);
        
        LOGI("Bootstrap.LaunchGame returned: %d", result);
        
        if (result == 0) {
            LOGI("✅ Bootstrap.LaunchGame completed successfully");
        } else if (result < 0) {
            LOGE("❌ Bootstrap.LaunchGame failed with error code: %d", result);
            switch (result) {
                case -1: LOGE("  → Unknown error"); break;
                case -2: LOGE("  → targetGamePathPtr is null"); break;
                case -3: LOGE("  → String parsing failed"); break;
                case -4: LOGE("  → String is empty"); break;
                case -5: LOGE("  → File does not exist"); break;
                case -6: LOGE("  → Callback not set"); break;
                case -7: LOGE("  → Initialization failed (general)"); break;
                case -71: LOGE("  → Cannot get directory name"); break;
                case -72: LOGE("  → Directory does not exist"); break;
                case -73: LOGE("  → Cannot set working directory"); break;
                case -74: LOGE("  → Basic environment setup failed"); break;
                case -75: LOGE("  → Assembly cache build failed"); break;
                case -76: LOGE("  → GetEntryAssembly patch failed"); break;
                case -77: LOGE("  → LoggingHooks patch failed"); break;
                case -78: LOGE("  → TryFixFileCasings patch failed"); break;
                case -79: LOGE("  → ApplyPatch failed"); break;
                case -8: LOGE("  → Callback execution failed"); break;
                default: LOGE("  → Unknown error code"); break;
            }
        } else {
            LOGW("⚠️ Bootstrap.LaunchGame returned non-zero code: %d", result);
        }
        
        exitCode = (unsigned int)result;
    } else {
        LOGI("🎮 Using direct launch mode");
        LOGI("   Game Assembly: %s", h_appPath);
        
        // 直接启动模式
        const char* directArgs[] = { h_appPath };
        LOGI("🎯 Executing assembly: %s", h_appPath);
        rc = coreclr_execute_assembly(hostHandle, domainId, 1, directArgs, h_appPath, &exitCode);
        
        if (rc != 0) {
            const char* error_msg = get_coreclr_error_message(rc);
            LOGE("❌ Assembly execution FAILED!");
            LOGE("   Error Code: 0x%08X (%d)", rc, rc);
            LOGE("   Error Message: %s", error_msg);
            LOGE("   Assembly: %s", h_appPath);
            LOGE("   Exit Code: %u", exitCode);
            LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            LOGE("Possible causes:");
            LOGE("  1. Entry point (Main method) not found");
            LOGE("  2. Assembly dependencies missing");
            LOGE("  3. Runtime exception in managed code");
            LOGE("  4. Incorrect assembly format");
            LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else if (exitCode != 0) {
            LOGW("⚠️  Assembly executed but returned non-zero exit code: %u", exitCode);
        } else {
            LOGI("✅ Assembly executed successfully");
        }
    }
    
    // 10. 关闭 CoreCLR 运行时（如果函数可用）
    if (coreclr_shutdown) {
        LOGI("Calling coreclr_shutdown");
        coreclr_shutdown(hostHandle, domainId);
    } else {
        LOGW("Skipping coreclr_shutdown (not available in this .NET version)");
    }
    
    // 11. 卸载 CoreCLR 动态库
    dlclose(coreclrLib);
    
    // 12. 返回退出码
    LOGI("CoreCLR execution finished with result: %d", rc == 0 ? (int)exitCode : -20);
    return rc == 0 ? (int)exitCode : -20;
}


