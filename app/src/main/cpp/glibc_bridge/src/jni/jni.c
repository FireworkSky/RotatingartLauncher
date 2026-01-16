/*
 * glibc-bridge JNI 实现
 * 
 * glibc-bridge 的 JNI 包装，为 Java 提供运行时访问
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#include "../include/api.h"

#define LOG_TAG "GlibcBridgeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/* Global state */
static char g_files_dir[512] = {0};
static char g_rootfs_path[512] = {0};
static int g_initialized = 0;

/* ============================================================================
 * JNI: NativeBridge.init
 * ============================================================================ */

JNIEXPORT jint JNICALL
Java_com_app_ralaunch_box64_NativeBridge_init(JNIEnv *env, jclass clazz,
                                               jobject context, jstring filesDir) {
    if (g_initialized) {
        LOGI("glibc-bridge already initialized");
        return 0;
    }

    if (!filesDir) {
        LOGE("filesDir is null");
        return -1;
    }

    /* Get files directory path */
    const char *files_dir_str = (*env)->GetStringUTFChars(env, filesDir, NULL);
    if (!files_dir_str) {
        LOGE("Failed to get filesDir string");
        return -1;
    }

    strncpy(g_files_dir, files_dir_str, sizeof(g_files_dir) - 1);
    g_files_dir[sizeof(g_files_dir) - 1] = '\0';
    
    /* Set rootfs path (rootfs.tar.xz 解压后的路径) */
    snprintf(g_rootfs_path, sizeof(g_rootfs_path), "%s/rootfs", g_files_dir);
    
    (*env)->ReleaseStringUTFChars(env, filesDir, files_dir_str);

    LOGI("glibc-bridge JNI initialized");
    LOGI("  Files dir: %s", g_files_dir);
    LOGI("  Rootfs path: %s", g_rootfs_path);

    g_initialized = 1;
    return 0;
}

/* ============================================================================
 * JNI: NativeBridge.run
 * ============================================================================ */

JNIEXPORT jint JNICALL
Java_com_app_ralaunch_box64_NativeBridge_run(JNIEnv *env, jclass clazz,
                                              jstring programPath, jobjectArray args,
                                              jstring rootfsPath) {
    if (!programPath) {
        LOGE("programPath is null");
        return -1;
    }

    const char *program_path_str = (*env)->GetStringUTFChars(env, programPath, NULL);
    if (!program_path_str) {
        LOGE("Failed to get programPath string");
        return -1;
    }

    /* Get rootfs path */
    const char *rootfs_str = NULL;
    if (rootfsPath) {
        rootfs_str = (*env)->GetStringUTFChars(env, rootfsPath, NULL);
    }
    const char *effective_rootfs = rootfs_str ? rootfs_str : g_rootfs_path;

    /* Build argv array */
    int argc = 1;
    char **argv = NULL;
    
    if (args) {
        int args_len = (*env)->GetArrayLength(env, args);
        argc = args_len + 1;
        argv = (char**)malloc(sizeof(char*) * (argc + 1));
        if (!argv) {
            LOGE("Failed to allocate argv");
            (*env)->ReleaseStringUTFChars(env, programPath, program_path_str);
            if (rootfs_str) (*env)->ReleaseStringUTFChars(env, rootfsPath, rootfs_str);
            return -1;
        }
        
        argv[0] = strdup(program_path_str);
        for (int i = 0; i < args_len; i++) {
            jstring arg = (jstring)(*env)->GetObjectArrayElement(env, args, i);
            const char *arg_str = (*env)->GetStringUTFChars(env, arg, NULL);
            argv[i + 1] = strdup(arg_str);
            (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        }
        argv[argc] = NULL;
    } else {
        argv = (char**)malloc(sizeof(char*) * 2);
        argv[0] = strdup(program_path_str);
        argv[1] = NULL;
    }

    LOGI("Running: %s with %d args", program_path_str, argc - 1);

    /* Execute */
    int result = glibc_bridge_execute(program_path_str, argc, argv, NULL, effective_rootfs);

    /* Cleanup */
    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);

    (*env)->ReleaseStringUTFChars(env, programPath, program_path_str);
    if (rootfs_str) (*env)->ReleaseStringUTFChars(env, rootfsPath, rootfs_str);

    LOGI("Execution completed with code: %d", result);
    return result;
}

/* ============================================================================
 * JNI: NativeBridge.runWithEnv
 * ============================================================================ */

JNIEXPORT jint JNICALL
Java_com_app_ralaunch_box64_NativeBridge_runWithEnv(JNIEnv *env, jclass clazz,
                                                     jstring programPath, jobjectArray args,
                                                     jobjectArray envp, jstring rootfsPath) {
    if (!programPath) {
        LOGE("programPath is null");
        return -1;
    }

    const char *program_path_str = (*env)->GetStringUTFChars(env, programPath, NULL);
    if (!program_path_str) {
        LOGE("Failed to get programPath string");
        return -1;
    }

    /* Get rootfs path */
    const char *rootfs_str = NULL;
    if (rootfsPath) {
        rootfs_str = (*env)->GetStringUTFChars(env, rootfsPath, NULL);
    }
    const char *effective_rootfs = rootfs_str ? rootfs_str : g_rootfs_path;

    /* Build argv array */
    int argc = 1;
    char **argv = NULL;
    
    if (args) {
        int args_len = (*env)->GetArrayLength(env, args);
        argc = args_len + 1;
        argv = (char**)malloc(sizeof(char*) * (argc + 1));
        if (!argv) {
            LOGE("Failed to allocate argv");
            (*env)->ReleaseStringUTFChars(env, programPath, program_path_str);
            if (rootfs_str) (*env)->ReleaseStringUTFChars(env, rootfsPath, rootfs_str);
            return -1;
        }
        
        argv[0] = strdup(program_path_str);
        for (int i = 0; i < args_len; i++) {
            jstring arg = (jstring)(*env)->GetObjectArrayElement(env, args, i);
            const char *arg_str = (*env)->GetStringUTFChars(env, arg, NULL);
            argv[i + 1] = strdup(arg_str);
            (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        }
        argv[argc] = NULL;
    } else {
        argv = (char**)malloc(sizeof(char*) * 2);
        argv[0] = strdup(program_path_str);
        argv[1] = NULL;
    }

    /* Build envp array */
    char **envp_arr = NULL;
    int envp_len = 0;
    
    if (envp) {
        envp_len = (*env)->GetArrayLength(env, envp);
        envp_arr = (char**)malloc(sizeof(char*) * (envp_len + 1));
        if (!envp_arr) {
            LOGE("Failed to allocate envp");
            for (int i = 0; i < argc; i++) free(argv[i]);
            free(argv);
            (*env)->ReleaseStringUTFChars(env, programPath, program_path_str);
            if (rootfs_str) (*env)->ReleaseStringUTFChars(env, rootfsPath, rootfs_str);
            return -1;
        }
        
        for (int i = 0; i < envp_len; i++) {
            jstring env_var = (jstring)(*env)->GetObjectArrayElement(env, envp, i);
            const char *env_str = (*env)->GetStringUTFChars(env, env_var, NULL);
            envp_arr[i] = strdup(env_str);
            LOGD("ENV[%d]: %s", i, env_str);
            (*env)->ReleaseStringUTFChars(env, env_var, env_str);
        }
        envp_arr[envp_len] = NULL;
    }

    LOGI("Running: %s with %d args, %d env vars", program_path_str, argc - 1, envp_len);
    LOGI("  Rootfs: %s", effective_rootfs);

    /* Execute */
    int result = glibc_bridge_execute(program_path_str, argc, argv, envp_arr, effective_rootfs);

    /* Cleanup argv */
    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);

    /* Cleanup envp */
    if (envp_arr) {
        for (int i = 0; i < envp_len; i++) {
            free(envp_arr[i]);
        }
        free(envp_arr);
    }

    (*env)->ReleaseStringUTFChars(env, programPath, program_path_str);
    if (rootfs_str) (*env)->ReleaseStringUTFChars(env, rootfsPath, rootfs_str);

    LOGI("Execution completed with code: %d", result);
    return result;
}

/* ============================================================================
 * JNI_OnLoad - Register natives
 * ============================================================================ */

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    LOGI("glibc-bridge JNI library loaded");
    return JNI_VERSION_1_6;
}

