/*
 * JNI bindings for native logger
 */

#include <jni.h>
#include "app_logger.h"

JNIEXPORT void JNICALL
Java_com_app_ralaunch_utils_AppLogger_initNativeLogger(JNIEnv* env, jclass clazz, jstring logDir) {
    const char* log_dir_str = (*env)->GetStringUTFChars(env, logDir, NULL);
    if (log_dir_str) {
        app_logger_init(log_dir_str);
        (*env)->ReleaseStringUTFChars(env, logDir, log_dir_str);
    }
}

JNIEXPORT void JNICALL
Java_com_app_ralaunch_utils_AppLogger_closeNativeLogger(JNIEnv* env, jclass clazz) {
    app_logger_close();
}
