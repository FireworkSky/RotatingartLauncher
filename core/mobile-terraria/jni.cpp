#include "converter.h"
#include "control_art.h"

#include <jni.h>

namespace {
std::string path(JNIEnv *env, jstring value)
{
    const char *chars = env->GetStringUTFChars(value, nullptr);
    terraria::require(chars != nullptr, "Unable to read converter path");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_feature_terraria_conversion_MobileTerrariaConverter_extractControlsNative(
    JNIEnv *env, jobject, jstring workspace, jstring output)
{
    try {
        const auto directory = terraria::fs::path(path(env, workspace));
        const terraria::UnityResources unity(directory, [](auto, auto, auto) {}, false);
        return terraria::extractControlArt(unity, terraria::readAtlasRegions(unity),
            directory / "control-recipes", path(env, output));
    } catch (const std::exception &failure) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), failure.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_feature_terraria_conversion_MobileTerrariaConverter_convertNative(
    JNIEnv *env, jobject, jstring workspace, jstring output, jobject callback)
{
    try {
        jclass type = env->GetObjectClass(callback);
        jmethodID update = env->GetMethodID(type, "update", "(Ljava/lang/String;II)Z");
        env->DeleteLocalRef(type);
        terraria::require(update != nullptr, "Mobile conversion progress method is missing");
        const auto report = terraria::convert(path(env, workspace), path(env, output),
            [&](const char *stage, int current, int total) {
                jstring label = env->NewStringUTF(stage);
                terraria::require(label != nullptr, "Unable to report conversion progress");
                bool active = env->CallBooleanMethod(callback, update, label, current, total);
                env->DeleteLocalRef(label);
                if (env->ExceptionCheck()) throw std::runtime_error("Conversion progress callback failed");
                if (!active) throw std::runtime_error("Mobile Terraria conversion cancelled");
            });
        return env->NewStringUTF(report.c_str());
    } catch (const std::exception &failure) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), failure.what());
        return nullptr;
    }
}
