#ifndef COREHOST_TRACE_REDIRECT_H
#define COREHOST_TRACE_REDIRECT_H

#ifdef __cplusplus
extern "C" {
#endif

// 初始化COREHOST_TRACE重定向到logcat
void init_corehost_trace_redirect();

// 清理hook
void cleanup_corehost_trace_redirect();

#ifdef __cplusplus
}
#endif

#endif // COREHOST_TRACE_REDIRECT_H
