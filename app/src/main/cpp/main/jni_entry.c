#include "jni_entry.h"

#include <android/log.h>

#define APP_LOG_NO_MACROS
#include "logging/app_log.h"
#undef APP_LOG_NO_MACROS

#define LOG_TAG "JNIEntry"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM* g_jvm = NULL;
static __thread int g_thread_attached = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;

    LOGI("JNI_OnLoad called");
    g_jvm = vm;

    JNIEnv* env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        app_log_init(env);
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)reserved;

    LOGI("JNI_OnUnload called");

    JNIEnv* env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        app_log_shutdown(env);
    }

    g_jvm = NULL;
    g_thread_attached = 0;
}

JNIEnv* JniEntry_GetEnv(void) {
    if (g_jvm == NULL) {
        LOGE("JavaVM is NULL in JniEntry_GetEnv");
        return NULL;
    }

    JNIEnv* env = NULL;
    jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);

    if (result == JNI_EDETACHED) {
        LOGI("Current thread not attached, attaching now");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("Failed to attach current thread to JVM");
            return NULL;
        }
        g_thread_attached = 1;
    } else if (result != JNI_OK) {
        LOGE("Failed to get JNIEnv, error code: %d", result);
        return NULL;
    }

    return env;
}

void JniEntry_SafeDetachEnv(void) {
    if (g_jvm == NULL || !g_thread_attached) {
        return;
    }

    JNIEnv* env = NULL;
    if ((*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
        g_thread_attached = 0;
        LOGI("Thread safely detached from JVM");
    }
}

JavaVM* JniEntry_GetJavaVM(void) {
    return g_jvm;
}
