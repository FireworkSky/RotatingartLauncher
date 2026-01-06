#include "dotnet_launcher.hpp"

#include <jni.h>
#include "app_logger.h"

#include "netcorehost/nethost.hpp"
#include "netcorehost/hostfxr.hpp"
#include "netcorehost/context.hpp"
#include "netcorehost/error.hpp"
#include "netcorehost/bindings.hpp"
#include "netcorehost/delegate_loader.hpp"

#define LOG_TAG "DotNetLauncher"

using namespace ral::dotnet;

std::string dotnet_launcher::hostfxr_last_error_msg;

int dotnet_launcher::hostfxr_launch(const std::string& assembly_path,
                                    std::vector<std::string> args,
                                    const std::string& dotnet_root) {
    try {
        // Reset error string
        hostfxr_last_error_msg = "";

        LOGI(LOG_TAG, "Loading hostfxr...");
        auto hostfxr = netcorehost::Nethost::load_hostfxr();
        if (!hostfxr) {
            LOGE(LOG_TAG, "hostfxr loading failed: returned null pointer");
            hostfxr_last_error_msg = "hostfxr loading failed: returned null pointer";
            return -1;
        }
        LOGI(LOG_TAG, "hostfxr loaded successfully");

        LOGI(LOG_TAG, "Initializing .NET runtime...");
        const char* argv[args.size()];
        for (size_t i = 0; i < args.size(); i++) {
            argv[i] = args[i].c_str();
        }
        int argc = static_cast<int>(args.size());

        auto context = hostfxr->initialize_for_dotnet_command_line_with_args_and_dotnet_root(
                netcorehost::PdCString::from_str(assembly_path),
                argc,
                argv,
                netcorehost::PdCString::from_str(dotnet_root)
        );

        if (!context) {
            LOGE(LOG_TAG, "Failed to initialize .NET runtime: returned null context");
            hostfxr_last_error_msg = "Failed to initialize .NET runtime: returned null context";
            return -1;
        }
        LOGI(LOG_TAG, ".NET runtime initialized successfully");

        LOGI(LOG_TAG, "Running application...");
        auto app_result = context->run_app();

        auto exit_code = app_result.value();

        if (exit_code == 0) {
            LOGI(LOG_TAG, "Application exited normally");
            hostfxr_last_error_msg = "";
        } else {
            auto hosting_result = app_result.as_hosting_result();
            std::string error_msg = hosting_result.get_error_message();
            LOGE(LOG_TAG, "Hosting error (code: %d)", exit_code);
            LOGE(LOG_TAG, "  %s", error_msg.c_str());
            hostfxr_last_error_msg = error_msg;
        }

        return exit_code;
    } catch (const netcorehost::HostingException& ex) {
        LOGE(LOG_TAG, "Hosting error");
        LOGE(LOG_TAG, "  %s", ex.what());
        hostfxr_last_error_msg = std::string("Hosting error: ") + ex.what();
        return -1;
    } catch (const std::exception& ex) {
        LOGE(LOG_TAG, "Unexpected error");
        LOGE(LOG_TAG, "  %s", ex.what());
        hostfxr_last_error_msg = std::string("Unexpected error: ") + ex.what();
        return -2;
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_dotnet_DotNetLauncher_getNativeDotNetLauncherHostfxrLastErrorMsg(JNIEnv *env,
                                                                                       jobject thiz) {
    return env->NewStringUTF(ral::dotnet::dotnet_launcher::hostfxr_last_error_msg.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_dotnet_DotNetLauncher_nativeDotNetLauncherHostfxrLaunch(JNIEnv *env,
                                                                              jobject thiz,
                                                                              jstring assembly_path,
                                                                              jobjectArray args,
                                                                              jstring dotnet_root) {
    // Convert jstring assembly_path to std::string
    const char* assembly_path_cstr = env->GetStringUTFChars(assembly_path, nullptr);
    std::string assembly_path_str(assembly_path_cstr);
    env->ReleaseStringUTFChars(assembly_path, assembly_path_cstr);

    // Convert jstring dotnet_root to std::string
    const char* dotnet_root_cstr = env->GetStringUTFChars(dotnet_root, nullptr);
    std::string dotnet_root_str(dotnet_root_cstr);
    env->ReleaseStringUTFChars(dotnet_root, dotnet_root_cstr);

    // Convert jobjectArray args to std::vector<std::string>
    std::vector<std::string> args_vec;
    jsize args_length = env->GetArrayLength(args);
    for (jsize i = 0; i < args_length; i++) {
        auto arg = (jstring)env->GetObjectArrayElement(args, i);
        const char* arg_cstr = env->GetStringUTFChars(arg, nullptr);
        args_vec.emplace_back(arg_cstr);
        env->ReleaseStringUTFChars(arg, arg_cstr);
        env->DeleteLocalRef(arg);
    }

    // Call the hostfxr_launch method
    return ral::dotnet::dotnet_launcher::hostfxr_launch(assembly_path_str, args_vec, dotnet_root_str);
}