
#include <jni.h>
#include <mutex>

#include "logger.hpp"
#include "dotnet/dotnet_launcher.hpp"

#include "dotnethost/nethost.hpp"
#include "dotnethost/hostfxr.hpp"
#include "dotnethost/context.hpp"
#include "dotnethost/error.hpp"

using namespace RALauncher::Dotnet;

// ========== 线程安全的错误消息 ==========

static std::mutex s_error_mutex;
static std::string s_last_error_msg;

static void set_last_error(const std::string& msg) {
    std::lock_guard<std::mutex> lock(s_error_mutex);
    s_last_error_msg = msg;
}

static std::string get_last_error() {
    std::lock_guard<std::mutex> lock(s_error_mutex);
    return s_last_error_msg;
}

// ========== hostfxr 错误输出捕获 ==========

static std::mutex s_error_writer_mutex;
static std::string s_error_writer_buffer;

static void hostfxr_error_writer_callback(const char* message) {
    if (message) {
        std::lock_guard<std::mutex> lock(s_error_writer_mutex);
        if (!s_error_writer_buffer.empty()) {
            s_error_writer_buffer += "\n";
        }
        s_error_writer_buffer += message;
        LOGE("[hostfxr] {}", message);
    }
}

static std::string drain_error_writer_buffer() {
    std::lock_guard<std::mutex> lock(s_error_writer_mutex);
    std::string result = std::move(s_error_writer_buffer);
    s_error_writer_buffer.clear();
    return result;
}

// ========== 核心启动逻辑 ==========

int DotnetLauncher::hostfxr_launch(const std::string& assembly_path,
                                    std::vector<std::string> args,
                                    const std::string& dotnet_root) {
    try {
        set_last_error("");

        // 参数校验
        if (assembly_path.empty()) {
            set_last_error("Assembly path is empty");
            LOGE("Assembly path is empty");
            return -1;
        }
        if (dotnet_root.empty()) {
            set_last_error("Dotnet root path is empty");
            LOGE("Dotnet root path is empty");
            return -1;
        }

        LOGI("Loading hostfxr...");
        auto hostfxr = dotnethost::Nethost::load_hostfxr();
        if (!hostfxr) {
            const std::string msg = "hostfxr loading failed: returned null pointer";
            LOGE("{}", msg.c_str());
            set_last_error(msg);
            return -1;
        }
        LOGI("hostfxr loaded successfully");

        // 注册错误输出捕获
        hostfxr->set_error_writer(
            reinterpret_cast<dotnethost::bindings::hostfxr_error_writer_fn>(hostfxr_error_writer_callback)
        );

        LOGI("Initializing .NET runtime...");
        const char* argv_raw[args.size()];
        for (size_t i = 0; i < args.size(); i++) {
            argv_raw[i] = args[i].c_str();
        }
        int argc = static_cast<int>(args.size());

        auto context = hostfxr->initialize_for_command_line(
            dotnethost::PdCString::from_str(assembly_path),
            argc,
            argv_raw,
            dotnethost::PdCString::from_str(dotnet_root)
        );

        if (!context) {
            const std::string msg = "Failed to initialize .NET runtime: returned null context";
            LOGE("{}", msg.c_str());
            set_last_error(msg);
            return -1;
        }
        LOGI(".NET runtime initialized successfully");

        LOGI("Running application...");
        auto app_result = context->run_app();
        auto exit_code = app_result.value();

        if (exit_code == 0) {
            LOGI("Application exited normally");
            set_last_error("");
        } else {
            auto hosting_result = app_result.as_hosting_result();
            std::string error_msg = hosting_result.get_error_message();

            // 附加 hostfxr 错误输出
            std::string writer_errors = drain_error_writer_buffer();
            if (!writer_errors.empty()) {
                error_msg += "\n[hostfxr output]\n" + writer_errors;
            }

            LOGE("Hosting error (code: {}): {}", exit_code, error_msg.c_str());
            set_last_error(error_msg);
        }

        return exit_code;
    } catch (const dotnethost::HostingException& ex) {
        std::string error_msg = std::string("Hosting error: ") + ex.what();
        std::string writer_errors = drain_error_writer_buffer();
        if (!writer_errors.empty()) {
            error_msg += "\n[hostfxr output]\n" + writer_errors;
        }
        LOGE("{}", error_msg.c_str());
        set_last_error(error_msg);
        return -1;
    } catch (const std::exception& ex) {
        std::string error_msg = std::string("Unexpected error: ") + ex.what();
        LOGE("{}", error_msg.c_str());
        set_last_error(error_msg);
        return -2;
    }
}

// ========== JNI 接口 ==========

extern "C"
JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_core_platform_runtime_dotnet_DotNetLauncher_getNativeDotNetLauncherHostfxrLastErrorMsg(JNIEnv *env,
                                                                                       jobject thiz) {
    std::string msg = get_last_error();
    return env->NewStringUTF(msg.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_platform_runtime_dotnet_DotNetLauncher_nativeDotNetLauncherHostfxrLaunch(JNIEnv *env,
                                                                              jobject thiz,
                                                                              jstring assembly_path,
                                                                              jobjectArray args,
                                                                              jstring dotnet_root) {
    // JNI 参数安全校验
    if (assembly_path == nullptr) {
        set_last_error("JNI: assembly_path is null");
        return -1;
    }
    if (args == nullptr) {
        set_last_error("JNI: args array is null");
        return -1;
    }
    if (dotnet_root == nullptr) {
        set_last_error("JNI: dotnet_root is null");
        return -1;
    }

    const char* assembly_path_cstr = env->GetStringUTFChars(assembly_path, nullptr);
    if (assembly_path_cstr == nullptr) {
        set_last_error("JNI: failed to get assembly_path string");
        return -1;
    }
    std::string assembly_path_str(assembly_path_cstr);
    env->ReleaseStringUTFChars(assembly_path, assembly_path_cstr);

    const char* dotnet_root_cstr = env->GetStringUTFChars(dotnet_root, nullptr);
    if (dotnet_root_cstr == nullptr) {
        set_last_error("JNI: failed to get dotnet_root string");
        return -1;
    }
    std::string dotnet_root_str(dotnet_root_cstr);
    env->ReleaseStringUTFChars(dotnet_root, dotnet_root_cstr);

    std::vector<std::string> args_vec;
    jsize args_length = env->GetArrayLength(args);
    args_vec.reserve(args_length);
    for (jsize i = 0; i < args_length; i++) {
        auto arg = (jstring)env->GetObjectArrayElement(args, i);
        if (arg == nullptr) continue;
        const char* arg_cstr = env->GetStringUTFChars(arg, nullptr);
        if (arg_cstr != nullptr) {
            args_vec.emplace_back(arg_cstr);
            env->ReleaseStringUTFChars(arg, arg_cstr);
        }
        env->DeleteLocalRef(arg);
    }

    return RALauncher::Dotnet::DotnetLauncher::hostfxr_launch(assembly_path_str, args_vec, dotnet_root_str);
}
