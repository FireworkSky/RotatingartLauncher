#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <cstring>
#include <cstdio>
#include "logging/app_log.h"

#define NATIVE_TAG "NativeMethods"

extern "C"
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_common_util_NativeMethods_nativeChdir(JNIEnv *env, jclass clazz, jstring path) {
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

// ==================== stdin pipe ====================

static int s_stdin_write_fd = -1;

/**
 * 创建 stdin 管道，将 fd 0 (stdin) 重定向到管道的读端。
 * 返回管道写端的 fd，后续通过 nativeWriteStdin 写入。
 * 返回 -1 表示失败。
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_common_util_NativeMethods_nativeSetupStdinPipe(JNIEnv *env, jclass clazz) {
    int pipefd[2]; // [0]=read, [1]=write
    if (pipe(pipefd) != 0) {
        LOGE(NATIVE_TAG, "pipe() failed: %s", strerror(errno));
        return -1;
    }

    // 将 stdin (fd 0) 重定向到管道的读端
    if (dup2(pipefd[0], STDIN_FILENO) == -1) {
        LOGE(NATIVE_TAG, "dup2(pipe_read, stdin) failed: %s", strerror(errno));
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    }

    // 关闭原始读端（fd 0 已经是读端的副本了）
    close(pipefd[0]);

    // 关键：重置 C stdio 的 stdin FILE* 流
    // 清除可能缓存的 EOF/error 标志
    clearerr(stdin);
    // 设置为无缓冲模式，确保 .NET 的 Console.ReadLine() 能立即读到数据
    setvbuf(stdin, NULL, _IONBF, 0);
    // 通过 fdopen 重新关联 stdin FILE* 和 fd 0
    // （某些 C 库实现中 dup2 后 FILE* 可能仍指向旧的内部状态）
    FILE* new_stdin = fdopen(STDIN_FILENO, "r");
    if (new_stdin != NULL && new_stdin != stdin) {
        // 无法直接替换 stdin 指针，但 fdopen 确保了 fd 0 可被正常读取
        LOGI(NATIVE_TAG,
            "fdopen(stdin) returned new FILE* %p (stdin=%p)", new_stdin, stdin);
    }

    // 保存写端
    s_stdin_write_fd = pipefd[1];

    LOGI(NATIVE_TAG,
        "stdin pipe setup OK: write_fd=%d, stdin(fd0) -> pipe_read, isatty=%d",
        s_stdin_write_fd, isatty(STDIN_FILENO));
    return s_stdin_write_fd;
}

/**
 * 向 stdin 管道写入数据（自动追加换行符）
 * 返回写入的字节数，-1 表示失败。
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_common_util_NativeMethods_nativeWriteStdin(JNIEnv *env, jclass clazz, jstring input) {
    if (s_stdin_write_fd < 0) {
        LOGW(NATIVE_TAG, "stdin pipe not setup, ignoring write");
        return -1;
    }
    if (input == nullptr) {
        return -1;
    }

    const char *str = env->GetStringUTFChars(input, nullptr);
    if (str == nullptr) {
        return -1;
    }

    size_t len = strlen(str);
    // 写入内容
    ssize_t written = write(s_stdin_write_fd, str, len);
    // 写入换行符
    if (written >= 0) {
        write(s_stdin_write_fd, "\n", 1);
        written++;
    }

    LOGI(NATIVE_TAG,
        "stdin write: \"%s\" (%zd bytes)", str, written);

    env->ReleaseStringUTFChars(input, str);

    if (written < 0) {
        LOGE(NATIVE_TAG, "write() failed: %s", strerror(errno));
        return -1;
    }
    return (jint)written;
}

/**
 * 关闭 stdin 管道的写端
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_app_ralaunch_core_common_util_NativeMethods_nativeCloseStdinPipe(JNIEnv *env, jclass clazz) {
    if (s_stdin_write_fd >= 0) {
        close(s_stdin_write_fd);
        LOGI(NATIVE_TAG, "stdin pipe write_fd closed");
        s_stdin_write_fd = -1;
    }
}
