#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

void app_log_init(JNIEnv *env);
void app_log_shutdown(JNIEnv *env);

int app_log_v(const char *tag, const char *message);
int app_log_d(const char *tag, const char *message);
int app_log_i(const char *tag, const char *message);
int app_log_w(const char *tag, const char *message);
int app_log_e(const char *tag, const char *message);

int app_log_vf(const char *tag, const char *format, ...);
int app_log_df(const char *tag, const char *format, ...);
int app_log_if(const char *tag, const char *format, ...);
int app_log_wf(const char *tag, const char *format, ...);
int app_log_ef(const char *tag, const char *format, ...);

#ifdef __cplusplus
}
#endif

#ifndef APP_LOG_NO_MACROS
#define LOGV(tag, ...) app_log_vf((tag), __VA_ARGS__)
#define LOGD(tag, ...) app_log_df((tag), __VA_ARGS__)
#define LOGI(tag, ...) app_log_if((tag), __VA_ARGS__)
#define LOGW(tag, ...) app_log_wf((tag), __VA_ARGS__)
#define LOGE(tag, ...) app_log_ef((tag), __VA_ARGS__)
#endif
