/*
 * glibc-bridge TLS 兼容层
 * 
 * 提供 glibc TLS 与 Android bionic TLS 之间的兼容性
 * 
 * 问题：glibc 和 bionic 都使用 tpidr_el0 作为 TLS，但布局不同
 * 
 * bionic TLS 布局 (ARM64):
 *   tpidr_el0 指向 tls_slot(0)
 *   - TLS_SLOT_DTV = 0              // DTV 指针
 *   - TLS_SLOT_THREAD_ID = 1        // pthread_internal_t*
 *   - TLS_SLOT_OPENGL = 2
 *   - TLS_SLOT_OPENGL_API = 5
 *   - TLS_SLOT_STACK_GUARD = 6      // 栈金丝雀
 *   - TLS_SLOT_SANITIZER = 7
 *   - TLS_SLOT_BIONIC_TLS = -1      // 偏移 -8 字节
 *
 * glibc TLS 布局 (ARM64):
 *   tpidr_el0 指向 TCB（线程控制块）
 *   - tcb[0] = DTV 指针
 *   - tcb[1] = 私有数据
 *   - TCB 之前：struct pthread（约 2KB）
 *   - TCB 之后：DTV 数组
 *
 * 解决方案：保持 bionic TLS 不变，为访问 TLS 的 glibc 函数
 * （如 __ctype_b_loc、errno 等）提供兼容的包装函数
 */

#ifndef GLIBC_BRIDGE_TLS_H
#define GLIBC_BRIDGE_TLS_H

#include <stdint.h>
#include <stddef.h>

/* ============================================================================
 * bionic TLS 槽位定义（来自 platform_bionic）
 * ============================================================================ */

/* ARM64 bionic TLS 槽位 */
#define BIONIC_MIN_TLS_SLOT             (-2)
#define BIONIC_TLS_SLOT_NATIVE_BRIDGE   (-2)
#define BIONIC_TLS_SLOT_BIONIC_TLS      (-1)
#define BIONIC_TLS_SLOT_DTV             0
#define BIONIC_TLS_SLOT_THREAD_ID       1
#define BIONIC_TLS_SLOT_OPENGL          2
#define BIONIC_TLS_SLOT_OPENGL_API      5
#define BIONIC_TLS_SLOT_STACK_GUARD     6
#define BIONIC_TLS_SLOT_SANITIZER       7
#define BIONIC_MAX_TLS_SLOT             7
#define BIONIC_TLS_SLOTS                (BIONIC_MAX_TLS_SLOT - BIONIC_MIN_TLS_SLOT + 1)

/* ============================================================================
 * bionic TLS 访问（读取当前 bionic TLS）
 * ============================================================================ */

/* 获取当前 bionic TLS 指针（读取 tpidr_el0）*/
static inline void** bionic_get_tls(void) {
    void** result;
    __asm__ volatile("mrs %0, tpidr_el0" : "=r"(result));
    return result;
}

/* 设置 tpidr_el0（警告：这会同时影响 bionic 和 glibc 代码！）*/
static inline void bionic_set_tls(void* tls) {
    __asm__ volatile("msr tpidr_el0, %0" : : "r"(tls));
}

/* 获取特定 TLS 槽位的值 */
static inline void* bionic_get_tls_slot(int slot) {
    void** tls = bionic_get_tls();
    return tls[slot - BIONIC_MIN_TLS_SLOT];
}

/* ============================================================================
 * glibc TLS 模拟
 * 
 * 不设置真正的 glibc TLS（这会破坏 bionic），
 * 而是为 glibc 特定数据提供线程本地存储
 * ============================================================================ */

/* glibc ctype 表标志（匹配 glibc 实现）*/
#define _GLIBC_ISbit(bit)  ((bit) < 8 ? ((1 << (bit)) << 8) : ((1 << (bit)) >> 8))

#define _GLIBC_ISupper    _GLIBC_ISbit(0)   /* 大写字母 */
#define _GLIBC_ISlower    _GLIBC_ISbit(1)   /* 小写字母 */
#define _GLIBC_ISalpha    _GLIBC_ISbit(2)   /* 字母 */
#define _GLIBC_ISdigit    _GLIBC_ISbit(3)   /* 数字 */
#define _GLIBC_ISxdigit   _GLIBC_ISbit(4)   /* 十六进制数字 */
#define _GLIBC_ISspace    _GLIBC_ISbit(5)   /* 空白字符 */
#define _GLIBC_ISprint    _GLIBC_ISbit(6)   /* 可打印字符 */
#define _GLIBC_ISgraph    _GLIBC_ISbit(7)   /* 图形字符 */
#define _GLIBC_ISblank    _GLIBC_ISbit(8)   /* 空格/制表符 */
#define _GLIBC_IScntrl    _GLIBC_ISbit(9)   /* 控制字符 */
#define _GLIBC_ISpunct    _GLIBC_ISbit(10)  /* 标点符号 */
#define _GLIBC_ISalnum    _GLIBC_ISbit(11)  /* 字母或数字 */

/* glibc 兼容 TLS 数据结构
 * 重要：stack_guard 必须在偏移 0x28 处以保证 glibc 兼容性！
 * glibc 代码通过 FS:0x28（ARM64 上为 TPIDR_EL0:0x28）访问栈金丝雀
 */
typedef struct glibc_compat_tls {
    /* 填充以使 stack_guard 对齐到偏移 0x28 */
    uint64_t _reserved0;                /* 0x00 */
    uint64_t _reserved1;                /* 0x08 */
    uint64_t _reserved2;                /* 0x10 */
    uint64_t _reserved3;                /* 0x18 */
    uint64_t _reserved4;                /* 0x20 */
    
    /* 栈金丝雀 - 必须在偏移 0x28 以保证 glibc 兼容性 */
    uintptr_t stack_guard;              /* 0x28 */
    
    /* 其他字段在关键偏移之后 */
    const unsigned short* ctype_b;      /* 字符分类表 */
    const int* ctype_tolower;           /* 转小写表 */
    const int* ctype_toupper;           /* 转大写表 */
    int glibc_errno;                    /* glibc 程序的 errno */
    char* progname;                     /* 程序调用名 */
    char* progname_full;                /* 完整程序路径 */
    
} glibc_compat_tls_t;

/* 编译时验证 stack_guard 在偏移 0x28 */
#ifndef __cplusplus
_Static_assert(offsetof(glibc_compat_tls_t, stack_guard) == 0x28,
               "stack_guard must be at offset 0x28 for glibc compatibility");
#endif

/* 全局 glibc 兼容 TLS（包装代码中的线程本地）*/
extern __thread glibc_compat_tls_t g_glibc_tls;

/* ============================================================================
 * 初始化函数
 * ============================================================================ */

/* 初始化 glibc 兼容 TLS 层 */
void glibc_bridge_init_glibc_tls(void);

/* 从 bionic TLS 复制栈保护值到 glibc 兼容 TLS */
void glibc_bridge_sync_stack_guard(void);

/* 获取 errno 位置（用于 glibc __errno_location 包装）*/
int* glibc_bridge_errno_location(void);

/* 从 bionic 同步 errno 到 glibc（在 bionic 函数返回后调用）*/
void glibc_bridge_sync_errno_from_bionic(void);

/* 静默同步 errno（不记录错误）*/
void glibc_bridge_sync_errno_silent(void);

/* 调用 bionic 函数后同步 errno 的宏（记录错误）*/
#define SYNC_ERRNO() glibc_bridge_sync_errno_from_bionic()

/* 仅同步 errno 的宏（不记录，用于成功调用）*/
#define SYNC_ERRNO_SILENT() glibc_bridge_sync_errno_silent()

/* 智能同步 - 仅在调用失败时记录（int 返回 <0，指针返回 NULL）*/
#define SYNC_ERRNO_IF_FAIL(ret) do { \
    if ((long)(ret) < 0) { glibc_bridge_sync_errno_from_bionic(); } \
    else { glibc_bridge_sync_errno_silent(); } \
} while(0)

/* ============================================================================
 * ctype 包装（glibc 使用与 bionic 不同的表格式）
 * ============================================================================ */

/* 这些函数返回 glibc 格式 ctype 表的指针 */
const unsigned short** glibc_bridge_ctype_b_loc(void);
const int** glibc_bridge_ctype_tolower_loc(void);
const int** glibc_bridge_ctype_toupper_loc(void);

/* ============================================================================
 * 动态库 TLS 支持
 * 
 * 对于动态加载的 glibc 库（如 libcoreclr.so），需要提供
 * 与 TLSDESC 重定位配合使用的 TLS 存储
 * 
 * 问题：TLSDESC 解析器返回一个偏移量，调用者将其加到 TPIDR_EL0
 * 但 TPIDR_EL0 指向 bionic TLS，不是我们的存储
 * 
 * 解决方案：返回一个"假偏移"使得：
 *   TPIDR_EL0 + fake_offset = &our_tls_storage[real_offset]
 * ============================================================================ */

#define GLIBC_BRIDGE_DYNLIB_TLS_SIZE 65536  /* 动态库 TLS 64KB */

/* 获取动态库 TLS 存储的基地址 */
void* glibc_bridge_get_dynlib_tls_base(void);

/* TLSDESC 解析器函数 - 由 TLSDESC 机制调用 */
/* 这是汇编实现，这里声明供参考 */
void glibc_bridge_tlsdesc_resolver_static(void);

/* 汇编解析器调用的 C 实现 */
intptr_t glibc_bridge_tlsdesc_resolve_impl(void* desc);

#endif /* GLIBC_BRIDGE_TLS_H */
