#include <jni.h>
#include <dlfcn.h>
#include <cstdio>
#include <ctime>
#include <fstream>
#include <unistd.h>
#include <fcntl.h>
#include <cstdlib>
#include <mutex>
#include <string>
#include <vector>

#include "logger.hpp"
#include "mono/mono_launcher.hpp"

using namespace RALauncher::Mono;

// ===================== 错误信息 =====================

static std::mutex s_error_mutex;
static std::string s_last_error_msg;

static void set_last_error(const std::string& msg) {
    std::lock_guard<std::mutex> lock(s_error_mutex);
    s_last_error_msg = msg;
}

std::string MonoLauncher::last_error() {
    std::lock_guard<std::mutex> lock(s_error_mutex);
    return s_last_error_msg;
}

// ===================== Mono 嵌入 API（运行时解析） =====================
//
// 只拿到 mono 的共享库（libmonosgen-2.0.so），没有配套开发头文件，
// 因此这里声明所需函数的签名并在运行时 dlsym 解析。

namespace {

    typedef void* MonoDomain;
    typedef void* MonoAssembly;
    typedef void* MonoImage;

    typedef void (*mono_set_assemblies_path_fn)(const char*);
    typedef void (*mono_set_dirs_fn)(const char*, const char*);
    typedef MonoDomain (*mono_jit_init_version_fn)(const char*, const char*);
    typedef MonoAssembly (*mono_domain_assembly_open_fn)(MonoDomain, const char*);
    typedef int (*mono_jit_exec_fn)(MonoDomain, MonoAssembly, int, char**);
    typedef void (*mono_jit_cleanup_fn)(MonoDomain);
    typedef void (*mono_free_fn)(void*);
    typedef char* (*mono_get_runtime_build_info_fn)(void);

    // 未处理异常钩子（用于把异常详情落盘，避免只依赖 logcat 被厂商日志配额丢弃）
    typedef void (*mono_unhandled_exception_func)(void*, void*, void*);
    typedef void (*mono_install_unhandled_exception_hook_fn)(mono_unhandled_exception_func, void*);
    typedef char* (*mono_object_to_string_fn)(void*, void**);

    struct MonoApi {
        void* handle = nullptr;

        mono_set_assemblies_path_fn set_assemblies_path = nullptr;
        mono_set_dirs_fn set_dirs = nullptr;
        mono_jit_init_version_fn jit_init_version = nullptr;
        mono_domain_assembly_open_fn domain_assembly_open = nullptr;
        mono_jit_exec_fn jit_exec = nullptr;
        mono_jit_cleanup_fn jit_cleanup = nullptr;
        mono_free_fn mono_free = nullptr;
        mono_get_runtime_build_info_fn runtime_build_info = nullptr;
        mono_install_unhandled_exception_hook_fn install_unhandled_exception_hook = nullptr;
        mono_object_to_string_fn object_to_string = nullptr;

        bool valid() const {
            return handle && set_assemblies_path && jit_init_version &&
                   domain_assembly_open && jit_exec;
        }
    };

    std::mutex s_api_mutex;
    MonoApi s_api;
    bool s_api_loaded = false;
    MonoDomain s_domain = nullptr;

    void* resolve_symbol(void* handle, const char* name) {
        dlerror();
        void* symbol = dlsym(handle, name);
        const char* error = dlerror();
        if (error != nullptr) {
            LOGE("[mono] 符号解析失败 {}: {}", name, error);
            return nullptr;
        }
        return symbol;
    }

    /**
     * 加载 libmonosgen-2.0.so 并解析所需符号。
     * Kotlin 侧会用 System.loadLibrary("monosgen-2.0") 先行加载，
     * 这里再以 RTLD_GLOBAL 打开一次以便解析符号。
     */
    bool load_mono_api() {
        std::lock_guard<std::mutex> lock(s_api_mutex);
        if (s_api_loaded) return s_api.valid();

        // 先尝试按裸名打开（Android 会从应用 native 库目录解析），再回退到绝对路径
        void* handle = dlopen("libmonosgen-2.0.so", RTLD_NOW | RTLD_GLOBAL);
        if (handle == nullptr) {
            const char* error = dlerror();
            LOGE("[mono] dlopen(libmonosgen-2.0.so) 失败: {}", error ? error : "(unknown)");
            set_last_error(std::string("无法加载 libmonosgen-2.0.so: ") + (error ? error : "unknown"));
            return false;
        }

        s_api.handle = handle;
        s_api.set_assemblies_path = reinterpret_cast<mono_set_assemblies_path_fn>(
                resolve_symbol(handle, "mono_set_assemblies_path"));
        s_api.set_dirs = reinterpret_cast<mono_set_dirs_fn>(
                resolve_symbol(handle, "mono_set_dirs"));
        s_api.jit_init_version = reinterpret_cast<mono_jit_init_version_fn>(
                resolve_symbol(handle, "mono_jit_init_version"));
        s_api.domain_assembly_open = reinterpret_cast<mono_domain_assembly_open_fn>(
                resolve_symbol(handle, "mono_domain_assembly_open"));
        s_api.jit_exec = reinterpret_cast<mono_jit_exec_fn>(
                resolve_symbol(handle, "mono_jit_exec"));
        s_api.jit_cleanup = reinterpret_cast<mono_jit_cleanup_fn>(
                resolve_symbol(handle, "mono_jit_cleanup"));
        s_api.mono_free = reinterpret_cast<mono_free_fn>(
                resolve_symbol(handle, "mono_free"));
        s_api.runtime_build_info = reinterpret_cast<mono_get_runtime_build_info_fn>(
                resolve_symbol(handle, "mono_get_runtime_build_info"));
        s_api.install_unhandled_exception_hook =
                reinterpret_cast<mono_install_unhandled_exception_hook_fn>(
                        resolve_symbol(handle, "mono_install_unhandled_exception_hook"));
        s_api.object_to_string = reinterpret_cast<mono_object_to_string_fn>(
                resolve_symbol(handle, "mono_object_to_string"));

        s_api_loaded = true;

        if (!s_api.valid()) {
            set_last_error("Mono 嵌入 API 不完整（缺少关键符号）");
            return false;
        }

        if (s_api.runtime_build_info != nullptr) {
            char* build_info = s_api.runtime_build_info();
            if (build_info != nullptr) {
                LOGI("[mono] 运行时构建信息: {}", build_info);
                if (s_api.mono_free != nullptr) {
                    s_api.mono_free(build_info);
                }
            }
        }
        return true;
    }

    /** Mono 异常/崩溃落盘路径（由 launch() 设置，位于 Mono 运行时根目录下） */
    std::mutex s_crash_mutex;
    std::string s_crash_log_path;

    /**
     * 把一段文本追加写入崩溃日志文件。
     * 之所以写文件而不是只打日志：部分厂商 ROM 有 per-process 日志配额，
     * mono 的 "Unhandled Exception" 输出到 stderr 后会被直接丢弃。
     */
    void append_crash_log(const std::string& text) {
        if (s_crash_log_path.empty()) return;
        std::lock_guard<std::mutex> lock(s_crash_mutex);

        char time_buf[64];
        std::time_t now = std::time(nullptr);
        std::tm tm_buf{};
        localtime_r(&now, &tm_buf);
        std::strftime(time_buf, sizeof(time_buf), "%Y-%m-%d %H:%M:%S", &tm_buf);

        std::ofstream out(s_crash_log_path, std::ios::app);
        if (!out.is_open()) return;
        out << "[" << time_buf << "] " << text << "\n";
        out.flush();
    }

    int s_saved_stdout = -1;
    int s_saved_stderr = -1;

    /**
     * 把 stdout/stderr 重定向到文件。
     *
     * mono 与游戏（FNA/Terraria）的错误输出都走 stderr，而 Android 上这些内容
     * 最终会进入 logcat，在部分厂商 ROM 上会被 per-process 日志配额直接丢弃，
     * 这里直接落盘以保证诊断信息不丢。
     */
    bool redirect_std_streams(const std::string& path) {
        const int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (fd < 0) {
            LOGE("[mono] 无法打开输出文件 {}: {}", path, strerror(errno));
            return false;
        }
        s_saved_stdout = dup(STDOUT_FILENO);
        s_saved_stderr = dup(STDERR_FILENO);
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        close(fd);
        // 让 C 运行时的缓冲与新的 fd 对齐，避免缓冲内容丢失
        setvbuf(stdout, nullptr, _IONBF, 0);
        setvbuf(stderr, nullptr, _IONBF, 0);
        return true;
    }

    void restore_std_streams() {
        if (s_saved_stdout >= 0) {
            dup2(s_saved_stdout, STDOUT_FILENO);
            close(s_saved_stdout);
            s_saved_stdout = -1;
        }
        if (s_saved_stderr >= 0) {
            dup2(s_saved_stderr, STDERR_FILENO);
            close(s_saved_stderr);
            s_saved_stderr = -1;
        }
    }

    /** Mono 未处理异常回调 */
    void mono_unhandled_exception_callback(void* exception, void* sender, void* user_data) {
        (void) sender;
        (void) user_data;

        std::string text;
        if (s_api.object_to_string != nullptr && exception != nullptr) {
            void* pending = nullptr;
            char* str = s_api.object_to_string(exception, &pending);
            if (str != nullptr) {
                text = str;
                if (s_api.mono_free != nullptr) {
                    s_api.mono_free(str);
                }
            }
        }
        if (text.empty()) {
            text = "(mono 报告未处理异常，但无法取得异常文本)";
        }

        const std::string entry = "===== Mono 未处理异常 / Unhandled Exception =====\n" + text;
        LOGE("[mono] 未处理异常，详情已写入 {}:\n{}", s_crash_log_path, text);
        append_crash_log(entry);
        set_last_error(std::string("Mono 未处理异常: ") + text);
    }

    /** mono 需要程序集目录；确保它是绝对路径并解除末尾斜杠 */
    std::string normalize_dir(const std::string& path) {
        std::string result = path;
        while (result.size() > 1 && result.back() == '/') {
            result.pop_back();
        }
        return result;
    }

} // namespace

// ===================== 启动逻辑 =====================

int MonoLauncher::launch(const std::string& assembly_path,
                         std::vector<std::string> args,
                         const std::string& mono_root,
                         const std::string& bcl_dir) {
    try {
        set_last_error("");

        if (assembly_path.empty()) {
            set_last_error("Assembly path is empty");
            LOGE("[mono] 程序集路径为空");
            return -1;
        }
        if (mono_root.empty()) {
            set_last_error("Mono root path is empty");
            LOGE("[mono] Mono 根目录为空");
            return -1;
        }

        const std::string assemblies_dir = normalize_dir(
                bcl_dir.empty() ? (normalize_dir(mono_root) + "/bcl") : bcl_dir);
        const std::string mono_root_dir = normalize_dir(mono_root);

        LOGI("========================================");
        LOGI("[mono] 启动托管程序集");
        LOGI("[mono]   Assembly : {}", assembly_path);
        LOGI("[mono]   MonoRoot : {}", mono_root_dir);
        LOGI("[mono]   BCL dir  : {}", assemblies_dir);
        LOGI("========================================");

        if (!load_mono_api()) {
            const std::string msg = last_error();
            LOGE("[mono] 加载失败: {}", msg);
            return -1;
        }

        append_crash_log("step 1/6: 加载 mono 库完成");
        // 让 BCL 所在的 bcl/ 目录成为程序集搜索根（其中直接包含 mscorlib.dll）
        s_api.set_assemblies_path(assemblies_dir.c_str());
        append_crash_log("step 2/6: 设置程序集搜索路径 = " + assemblies_dir);

        // 同步设置 MONO_PATH，兼容 mono 内部在初始化早期读取该变量的路径
        setenv("MONO_PATH", assemblies_dir.c_str(), 1);
        // 注意：不要在这里覆盖 MONO_ENV_OPTIONS / MONO_LOG_*，
        // 它们由设置里的「Mono LLVM」与「Mono 详细日志」两个开关决定。

        if (s_api.set_dirs != nullptr) {
            // Mono 默认布局是 <assembly_dir>/mono/4.5，这里同时把 config 目录指向运行时根，
            // 真正的 BCL 查找由上面的 assemblies_path 负责。
            s_api.set_dirs(mono_root_dir.c_str(), mono_root_dir.c_str());
        }

        s_crash_log_path = mono_root_dir + "/mono-crash.log";
        append_crash_log("===== Mono 启动 =====\nassembly: " + assembly_path +
                         "\nbcl: " + assemblies_dir);

        if (s_domain == nullptr) {
            LOGI("[mono] 初始化运行时 (v4.0.30319)...");
            s_domain = s_api.jit_init_version("RALDomain", "v4.0.30319");
            if (s_domain == nullptr) {
                const std::string msg = "mono_jit_init_version 返回 null（Mono 运行时初始化失败）";
                set_last_error(msg);
                LOGE("[mono] {}", msg);
                return -1;
            }
        }
        LOGI("[mono] 运行时初始化完成");
        append_crash_log("step 3/6: mono 运行时初始化完成");

        if (s_api.install_unhandled_exception_hook != nullptr) {
            s_api.install_unhandled_exception_hook(mono_unhandled_exception_callback, nullptr);
            LOGI("[mono] 已注册未处理异常钩子");
        }

        append_crash_log("step 4/6: 准备打开程序集");
        LOGI("[mono] 打开程序集: {}", assembly_path);
        MonoAssembly assembly = s_api.domain_assembly_open(s_domain, assembly_path.c_str());
        if (assembly == nullptr) {
            const std::string msg = "无法打开程序集: " + assembly_path +
                                    "（请确认该文件存在且 BCL 目录完整）";
            set_last_error(msg);
            LOGE("[mono] {}", msg);
            return -1;
        }

        // 构造 argv：argv[0] 约定为程序集路径
        std::vector<std::string> argv_storage;
        argv_storage.reserve(args.size() + 1);
        argv_storage.push_back(assembly_path);
        for (const auto& arg : args) {
            argv_storage.push_back(arg);
        }
        std::vector<char*> argv_raw;
        argv_raw.reserve(argv_storage.size());
        for (auto& s : argv_storage) {
            argv_raw.push_back(const_cast<char*>(s.c_str()));
        }

        append_crash_log("step 5/6: 程序集已打开，开始执行（stdout/stderr 转入 mono-stdout.log）");
        LOGI("[mono] 执行程序集 (argc={})...", static_cast<int>(argv_raw.size()));

        const std::string std_log_path = mono_root_dir + "/mono-stdout.log";
        const bool redirected = redirect_std_streams(std_log_path);
        const int exit_code = s_api.jit_exec(
                s_domain, assembly, static_cast<int>(argv_raw.size()), argv_raw.data());
        if (redirected) {
            fflush(stdout);
            fflush(stderr);
            restore_std_streams();
        }
        append_crash_log("step 6/6: jit_exec 已返回，退出码 = " + std::to_string(exit_code));

        LOGI("[mono] 程序集执行结束，退出码: {}", exit_code);
        append_crash_log("===== 程序集结束，退出码: " + std::to_string(exit_code) + " =====");
        if (exit_code == 0) {
            set_last_error("");
        } else {
            set_last_error("程序集以非零退出码结束: " + std::to_string(exit_code));
        }
        return exit_code;
    } catch (const std::exception& ex) {
        const std::string msg = std::string("Mono 启动异常: ") + ex.what();
        LOGE("[mono] {}", msg);
        set_last_error(msg);
        return -2;
    }
}

// ===================== JNI 接口 =====================

extern "C"
JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_core_platform_runtime_mono_MonoLauncher_nativeMonoLauncherLastError(JNIEnv* env,
                                                                                          jobject thiz) {
    (void) thiz;
    const std::string msg = RALauncher::Mono::MonoLauncher::last_error();
    return env->NewStringUTF(msg.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_platform_runtime_mono_MonoLauncher_nativeMonoLauncherLaunch(JNIEnv* env,
                                                                                       jobject thiz,
                                                                                       jstring assembly_path,
                                                                                       jobjectArray args,
                                                                                       jstring mono_root,
                                                                                       jstring bcl_dir) {
    (void) thiz;

    if (assembly_path == nullptr) {
        set_last_error("JNI: assembly_path is null");
        return -1;
    }
    if (args == nullptr) {
        set_last_error("JNI: args array is null");
        return -1;
    }
    if (mono_root == nullptr) {
        set_last_error("JNI: mono_root is null");
        return -1;
    }

    const char* assembly_cstr = env->GetStringUTFChars(assembly_path, nullptr);
    if (assembly_cstr == nullptr) {
        set_last_error("JNI: failed to get assembly_path string");
        return -1;
    }
    std::string assembly_str(assembly_cstr);
    env->ReleaseStringUTFChars(assembly_path, assembly_cstr);

    const char* mono_root_cstr = env->GetStringUTFChars(mono_root, nullptr);
    if (mono_root_cstr == nullptr) {
        set_last_error("JNI: failed to get mono_root string");
        return -1;
    }
    std::string mono_root_str(mono_root_cstr);
    env->ReleaseStringUTFChars(mono_root, mono_root_cstr);

    std::string bcl_str;
    if (bcl_dir != nullptr) {
        const char* bcl_cstr = env->GetStringUTFChars(bcl_dir, nullptr);
        if (bcl_cstr != nullptr) {
            bcl_str = bcl_cstr;
            env->ReleaseStringUTFChars(bcl_dir, bcl_cstr);
        }
    }

    std::vector<std::string> args_vec;
    const jsize args_length = env->GetArrayLength(args);
    args_vec.reserve(args_length);
    for (jsize i = 0; i < args_length; i++) {
        auto arg = reinterpret_cast<jstring>(env->GetObjectArrayElement(args, i));
        if (arg == nullptr) continue;
        const char* arg_cstr = env->GetStringUTFChars(arg, nullptr);
        if (arg_cstr != nullptr) {
            args_vec.emplace_back(arg_cstr);
            env->ReleaseStringUTFChars(arg, arg_cstr);
        }
        env->DeleteLocalRef(arg);
    }

    return RALauncher::Mono::MonoLauncher::launch(assembly_str, args_vec, mono_root_str, bcl_str);
}
