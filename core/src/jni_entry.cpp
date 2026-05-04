#include "jni_entry.hpp"
#include "logger.hpp"

static JavaVM *g_jvm = NULL;
static __thread int g_thread_attached = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;

    RALauncher::Logger::init();

    LOGI("JNI_OnLoad called");
    g_jvm = vm;

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    (void) reserved;

    LOGI("JNI_OnUnload called");
    RALauncher::Logger::shutdown();

    g_jvm = nullptr;
    g_thread_attached = 0;
}

JNIEnv *JniEntry_GetEnv(void) {
    if (g_jvm == NULL) {
        LOGE("JavaVM is NULL in JniEntry_GetEnv");
        return NULL;
    }

    JNIEnv *env = NULL;
    jint result = g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6);

    if (result == JNI_EDETACHED) {
        LOGI("Current thread not attached, attaching now");
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("Failed to attach current thread to JVM");
            return NULL;
        }
        g_thread_attached = 1;
    } else if (result != JNI_OK) {
        LOGE("Failed to get JNIEnv, error code: {}", result);
        return NULL;
    }

    return env;
}

void JniEntry_SafeDetachEnv(void) {
    if (g_jvm == NULL || !g_thread_attached) {
        return;
    }

    JNIEnv *env = NULL;
    if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK) {
        g_jvm->DetachCurrentThread();
        g_thread_attached = 0;
        LOGI("Thread safely detached from JVM");
    }
}

JavaVM *JniEntry_GetJavaVM(void) {
    return g_jvm;
}
