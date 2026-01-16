/*
 * glibc-bridge 动态链接器 - 日志系统
 * 
 * 环境变量控制的多级别日志
 * 设置 GLIBC_BRIDGE_LOG_LEVEL 来控制详细程度（0-5）
 */

#ifndef GLIBC_BRIDGE_LOG_H
#define GLIBC_BRIDGE_LOG_H

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * 日志级别
 * 
 * 设置 GLIBC_BRIDGE_LOG_LEVEL 环境变量：
 *   0 = NONE（无日志）
 *   1 = 仅 ERROR
 *   2 = WARN + ERROR
 *   3 = INFO + WARN + ERROR（默认）
 *   4 = DEBUG + INFO + WARN + ERROR
 *   5 = TRACE（所有内容，包括符号解析）
 * ============================================================================ */

#define GLIBC_BRIDGE_DL_LOG_NONE   0
#define GLIBC_BRIDGE_DL_LOG_ERROR  1
#define GLIBC_BRIDGE_DL_LOG_WARN   2
#define GLIBC_BRIDGE_DL_LOG_INFO   3
#define GLIBC_BRIDGE_DL_LOG_DEBUG  4
#define GLIBC_BRIDGE_DL_LOG_TRACE  5

/* 获取当前日志级别 */
int glibc_bridge_dl_get_log_level(void);

/* 强制设置日志级别 */
void glibc_bridge_dl_set_log_level(int level);

/* 在指定级别记录消息 */
void glibc_bridge_dl_log(int level, const char* msg);

/* 便捷日志函数 */
void glibc_bridge_dl_log_error(const char* msg);
void glibc_bridge_dl_log_warn(const char* msg);
void glibc_bridge_dl_log_info(const char* msg);
void glibc_bridge_dl_log_debug(const char* msg);
void glibc_bridge_dl_log_trace(const char* msg);

/* 子进程日志辅助（异步信号安全）*/
void glibc_bridge_dl_child_log(const char *msg);

#ifdef __cplusplus
}
#endif

#endif /* GLIBC_BRIDGE_LOG_H */
