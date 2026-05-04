#include <jni.h>
#include <vector>
#include <string>

#include "game_launcher.hpp"
#include "jni_entry.hpp"
#include "logger.hpp"

#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"
extern "C" __attribute__((visibility("default")))
int game_launcher_launch_new_dotnet_process(const char *assembly_path,
                                            int argc,
                                            const char *argv[],
                                            const char *title,
                                            const char *game_id) {
    LOGI("========================================");
    LOGI("game_launcher_launch_new_dotnet_process called");
    LOGI("========================================");
    LOGI("  Assembly: {}", assembly_path ? assembly_path : "(null)");
    LOGI("  Argc: {}", argc);
    LOGI("  Title: {}", title ? title : "(null)");
    LOGI("  Game ID: {}", game_id ? game_id : "(null)");

    if (!assembly_path) {
        LOGE("Assembly path is null");
        return -1;
    }

    JNIEnv *env = JniEntry_GetEnv();
    if (env == nullptr) {
        LOGE("Failed to get JNIEnv");
        return -2;
    }

    // Convert argv to Java String array
    jobjectArray jArgs = nullptr;
    if (argc > 0 && argv != nullptr) {
        jclass stringClass = env->FindClass("java/lang/String");
        jArgs = env->NewObjectArray(argc, stringClass, nullptr);
        for (int i = 0; i < argc; i++) {
            if (argv[i] != nullptr) {
                jstring jArg = env->NewStringUTF(argv[i]);
                env->SetObjectArrayElement(jArgs, i, jArg);
                env->DeleteLocalRef(jArg);
            }
        }
        LOGI("  Converted %d arguments", argc);
        env->DeleteLocalRef(stringClass);
    }

    // Convert to Java strings
    jstring jAssemblyPath = env->NewStringUTF(assembly_path);
    jstring jTitle = env->NewStringUTF(title ? title : "Process");
    jstring jGameId = env->NewStringUTF(game_id ? game_id : "");

    // Get GameLauncher class
    jclass gameLauncherClass = env->FindClass(
            "com/app/ralaunch/core/platform/runtime/GameLauncher");
    if (gameLauncherClass == nullptr) {
        LOGE("Failed to find GameLauncher class");
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        env->DeleteLocalRef(jTitle);
        env->DeleteLocalRef(jGameId);
        return -3;
    }

    // Get launchNewDotNetProcess static method
    jmethodID launchMethod = env->GetStaticMethodID(gameLauncherClass, "launchNewDotNetProcess",
                                                    "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I");
    if (launchMethod == nullptr) {
        LOGE("Failed to find launchNewDotNetProcess method");
        env->DeleteLocalRef(gameLauncherClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        env->DeleteLocalRef(jTitle);
        env->DeleteLocalRef(jGameId);
        return -4;
    }

    // Call launchNewDotNetProcess
    LOGI("Calling GameLauncher.launchNewDotNetProcess...");
    jint result = env->CallStaticIntMethod(gameLauncherClass, launchMethod,
                                           jAssemblyPath, jArgs, jTitle, jGameId);

    // Cleanup all JNI local references
    env->DeleteLocalRef(gameLauncherClass);
    env->DeleteLocalRef(jAssemblyPath);
    if (jArgs) env->DeleteLocalRef(jArgs);
    env->DeleteLocalRef(jTitle);
    env->DeleteLocalRef(jGameId);

    LOGI("GameLauncher.launchNewDotNetProcess returned: {}", result);
    LOGI("========================================");
    return result;
}
#pragma clang diagnostic pop
