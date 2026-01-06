#include <jni.h>
#include <unistd.h>

extern "C"
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_utils_NativeMethods_nativeChdir(JNIEnv *env, jclass clazz, jstring path) {
    if (path == nullptr) {
        return -1;
    }

    const char *nativePath = env->GetStringUTFChars(path, nullptr);
    if (nativePath == nullptr) {
        return -1;
    }

    int result = chdir(nativePath);

    env->ReleaseStringUTFChars(path, nativePath);

    return result;
}