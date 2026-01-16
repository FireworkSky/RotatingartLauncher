/*
 * glibc-bridge - 搜索和排序函数包装
 * 
 * 包含 qsort, bsearch, tsearch, tfind, tdelete, twalk 等函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <search.h>
#include <errno.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* ============================================================================
 * qsort 包装
 * 
 * 处理 glibc qsort_r 的比较函数参数顺序差异
 * glibc: compar(a, b, arg)
 * bionic: compar(arg, a, b) - FreeBSD 风格
 * ============================================================================ */

/* 用于 qsort_r 转换的上下文 */
typedef struct {
    int (*glibc_compar)(const void*, const void*, void*);
    void* arg;
} qsort_wrapper_ctx_t;

static __thread qsort_wrapper_ctx_t g_qsort_ctx;

static int qsort_compar_adapter(const void* a, const void* b) {
    return g_qsort_ctx.glibc_compar(a, b, g_qsort_ctx.arg);
}

void qsort_wrapper(void* base, size_t nmemb, size_t size, 
                   int (*compar)(const void*, const void*)) {
    LOG_DEBUG("qsort_wrapper: base=%p, nmemb=%zu, size=%zu", base, nmemb, size);
    qsort(base, nmemb, size, compar);
}

void qsort_r_wrapper(void* base, size_t nmemb, size_t size,
                     int (*compar)(const void*, const void*, void*), void* arg) {
    LOG_DEBUG("qsort_r_wrapper: base=%p, nmemb=%zu, size=%zu", base, nmemb, size);
    
    /* 保存上下文并使用适配器 */
    g_qsort_ctx.glibc_compar = compar;
    g_qsort_ctx.arg = arg;
    
    qsort(base, nmemb, size, qsort_compar_adapter);
}

/* ============================================================================
 * bsearch 包装
 * ============================================================================ */

void* bsearch_wrapper(const void* key, const void* base, size_t nmemb,
                       size_t size, int (*compar)(const void*, const void*)) {
    return bsearch(key, base, nmemb, size, compar);
}

/* ============================================================================
 * 二叉搜索树函数 (tsearch 系列)
 * ============================================================================ */

void* tsearch_wrapper(const void* key, void** rootp,
                       int (*compar)(const void*, const void*)) {
    LOG_DEBUG("tsearch_wrapper: key=%p", key);
    return tsearch(key, rootp, compar);
}

void* tfind_wrapper(const void* key, void* const* rootp,
                     int (*compar)(const void*, const void*)) {
    LOG_DEBUG("tfind_wrapper: key=%p", key);
    return tfind(key, rootp, compar);
}

void* tdelete_wrapper(const void* key, void** rootp,
                       int (*compar)(const void*, const void*)) {
    LOG_DEBUG("tdelete_wrapper: key=%p", key);
    return tdelete(key, rootp, compar);
}



/* twalk 用户回调 */
static __thread void (*g_twalk_action)(const void*, VISIT, int);

static void twalk_action_adapter(const void* nodep, VISIT which, int depth) {
    if (g_twalk_action) {
        g_twalk_action(nodep, which, depth);
    }
}

void twalk_wrapper(const void* root, 
                    void (*action)(const void*, VISIT, int)) {
    LOG_DEBUG("twalk_wrapper: root=%p", root);
    
    g_twalk_action = action;
    twalk(root, twalk_action_adapter);
}

/* tdestroy (glibc 扩展) */
void tdestroy_wrapper(void* root, void (*free_node)(void* nodep)) {
    LOG_DEBUG("tdestroy_wrapper: root=%p", root);
    
    if (!root) return;
    
    /* tdestroy 是 glibc 扩展，bionic 可能没有，手动实现 */
#ifdef __ANDROID__
    /* Android bionic 有 tdestroy */
    tdestroy(root, free_node);
#else
    /* 简单实现：遍历并释放 */
    /* 注意：这是简化实现，可能不完全正确 */
    if (free_node) {
        free_node(*(void**)root);
    }
#endif
}

/* ============================================================================
 * 哈希表函数 (hsearch 系列)
 * ============================================================================ */

int hcreate_wrapper(size_t nel) {
    LOG_DEBUG("hcreate_wrapper: nel=%zu", nel);
    return hcreate(nel);
}

void hdestroy_wrapper(void) {
    LOG_DEBUG("hdestroy_wrapper");
    hdestroy();
}

ENTRY* hsearch_wrapper(ENTRY item, ACTION action) {
    LOG_DEBUG("hsearch_wrapper: key='%s', action=%d", 
              item.key ? item.key : "(null)", action);
    return hsearch(item, action);
}

/* hsearch_r (可重入版本) */
int hcreate_r_wrapper(size_t nel, struct hsearch_data* htab) {
    LOG_DEBUG("hcreate_r_wrapper: nel=%zu", nel);
    return hcreate_r(nel, htab);
}

void hdestroy_r_wrapper(struct hsearch_data* htab) {
    LOG_DEBUG("hdestroy_r_wrapper");
    hdestroy_r(htab);
}

int hsearch_r_wrapper(ENTRY item, ACTION action, ENTRY** retval,
                       struct hsearch_data* htab) {
    return hsearch_r(item, action, retval, htab);
}

/* ============================================================================
 * 线性搜索函数 (lfind/lsearch)
 * ============================================================================ */

void* lfind_wrapper(const void* key, const void* base, size_t* nmemb,
                     size_t size, int (*compar)(const void*, const void*)) {
    return lfind(key, base, nmemb, size, compar);
}

void* lsearch_wrapper(const void* key, void* base, size_t* nmemb,
                       size_t size, int (*compar)(const void*, const void*)) {
    return lsearch(key, base, nmemb, size, compar);
}

/* ============================================================================
 * insque / remque (双向链表)
 * ============================================================================ */

void insque_wrapper(void* elem, void* prev) {
    insque(elem, prev);
}

void remque_wrapper(void* elem) {
    remque(elem);
}
