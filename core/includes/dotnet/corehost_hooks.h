#ifndef COREHOST_HOOKS_H
#define COREHOST_HOOKS_H

#ifdef __cplusplus
extern "C" {
#endif

// 初始化 COREHOST_TRACE 到 logcat 的 hooks
void init_corehost_trace_hooks();

// 初始化 CoreCLR 兼容性 hooks
void init_corehost_compat_hooks();

// 清理hook
void cleanup_corehost_hooks();

#ifdef __cplusplus
}
#endif

#endif // COREHOST_HOOKS_H
