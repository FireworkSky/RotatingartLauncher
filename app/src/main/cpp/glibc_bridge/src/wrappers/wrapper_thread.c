/*
 * glibc-bridge - 线程相关函数包装
 * 
 * 包含 pthread_create, pthread_key_create 等线程管理函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <errno.h>

/* pthread_cancel 相关常量 - bionic 不支持 pthread_cancel */
#ifndef PTHREAD_CANCEL_ENABLE
#define PTHREAD_CANCEL_ENABLE   0
#define PTHREAD_CANCEL_DISABLE  1
#define PTHREAD_CANCEL_DEFERRED 0
#define PTHREAD_CANCEL_ASYNCHRONOUS 1
#endif

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* ============================================================================
 * pthread_create 包装
 * 
 * 需要确保新线程正确设置 TLS
 * ============================================================================ */

typedef struct {
    void* (*start_routine)(void*);
    void* arg;
} thread_wrapper_data_t;

static void* thread_start_wrapper(void* data) {
    thread_wrapper_data_t* wrapper_data = (thread_wrapper_data_t*)data;
    void* (*start_routine)(void*) = wrapper_data->start_routine;
    void* arg = wrapper_data->arg;
    
    free(wrapper_data);
    
    /* 初始化新线程的 TLS（如果需要） */
    /* glibc_bridge_init_thread_tls(); */
    
    LOG_DEBUG("线程开始执行: start_routine=%p", (void*)start_routine);
    
    return start_routine(arg);
}

int pthread_create_wrapper(pthread_t* thread, const pthread_attr_t* attr,
                            void* (*start_routine)(void*), void* arg) {
    LOG_DEBUG("pthread_create_wrapper: start_routine=%p, arg=%p", 
              (void*)start_routine, arg);
    
    thread_wrapper_data_t* wrapper_data = malloc(sizeof(thread_wrapper_data_t));
    if (!wrapper_data) {
        return ENOMEM;
    }
    
    wrapper_data->start_routine = start_routine;
    wrapper_data->arg = arg;
    
    int result = pthread_create(thread, attr, thread_start_wrapper, wrapper_data);
    
    if (result != 0) {
        free(wrapper_data);
    }
    
    return result;
}

/* ============================================================================
 * pthread_key_create 包装
 * ============================================================================ */

int pthread_key_create_wrapper(pthread_key_t* key, void (*destructor)(void*)) {
    LOG_DEBUG("pthread_key_create_wrapper: key=%p, destructor=%p",
              (void*)key, (void*)destructor);
    
    int result = pthread_key_create(key, destructor);
    
    if (result == 0) {
        LOG_DEBUG("pthread_key_create_wrapper: 创建成功, key=%u", (unsigned)*key);
    }
    
    return result;
}

/* ============================================================================
 * pthread_key_delete 包装
 * ============================================================================ */

int pthread_key_delete_wrapper(pthread_key_t key) {
    LOG_DEBUG("pthread_key_delete_wrapper: key=%u", (unsigned)key);
    return pthread_key_delete(key);
}

/* ============================================================================
 * pthread_getspecific / pthread_setspecific 包装
 * ============================================================================ */

void* pthread_getspecific_wrapper(pthread_key_t key) {
    return pthread_getspecific(key);
}

int pthread_setspecific_wrapper(pthread_key_t key, const void* value) {
    return pthread_setspecific(key, value);
}

/* ============================================================================
 * pthread_mutex 包装
 * ============================================================================ */

int pthread_mutex_init_wrapper(pthread_mutex_t* mutex, 
                                const pthread_mutexattr_t* attr) {
    return pthread_mutex_init(mutex, attr);
}

int pthread_mutex_destroy_wrapper(pthread_mutex_t* mutex) {
    return pthread_mutex_destroy(mutex);
}

int pthread_mutex_lock_wrapper(pthread_mutex_t* mutex) {
    return pthread_mutex_lock(mutex);
}

int pthread_mutex_trylock_wrapper(pthread_mutex_t* mutex) {
    return pthread_mutex_trylock(mutex);
}

int pthread_mutex_unlock_wrapper(pthread_mutex_t* mutex) {
    return pthread_mutex_unlock(mutex);
}

/* ============================================================================
 * pthread_cond 包装
 * ============================================================================ */

int pthread_cond_init_wrapper(pthread_cond_t* cond, 
                               const pthread_condattr_t* attr) {
    return pthread_cond_init(cond, attr);
}

int pthread_cond_destroy_wrapper(pthread_cond_t* cond) {
    return pthread_cond_destroy(cond);
}

int pthread_cond_wait_wrapper(pthread_cond_t* cond, pthread_mutex_t* mutex) {
    return pthread_cond_wait(cond, mutex);
}

int pthread_cond_signal_wrapper(pthread_cond_t* cond) {
    return pthread_cond_signal(cond);
}

int pthread_cond_broadcast_wrapper(pthread_cond_t* cond) {
    return pthread_cond_broadcast(cond);
}

/* ============================================================================
 * pthread_rwlock 包装
 * ============================================================================ */

int pthread_rwlock_init_wrapper(pthread_rwlock_t* rwlock,
                                 const pthread_rwlockattr_t* attr) {
    return pthread_rwlock_init(rwlock, attr);
}

int pthread_rwlock_destroy_wrapper(pthread_rwlock_t* rwlock) {
    return pthread_rwlock_destroy(rwlock);
}

int pthread_rwlock_rdlock_wrapper(pthread_rwlock_t* rwlock) {
    return pthread_rwlock_rdlock(rwlock);
}

int pthread_rwlock_wrlock_wrapper(pthread_rwlock_t* rwlock) {
    return pthread_rwlock_wrlock(rwlock);
}

int pthread_rwlock_unlock_wrapper(pthread_rwlock_t* rwlock) {
    return pthread_rwlock_unlock(rwlock);
}

/* ============================================================================
 * pthread_once 包装
 * ============================================================================ */

int pthread_once_wrapper(pthread_once_t* once_control, void (*init_routine)(void)) {
    return pthread_once(once_control, init_routine);
}

/* ============================================================================
 * pthread 属性包装
 * ============================================================================ */

int pthread_attr_init_wrapper(pthread_attr_t* attr) {
    return pthread_attr_init(attr);
}

int pthread_attr_destroy_wrapper(pthread_attr_t* attr) {
    return pthread_attr_destroy(attr);
}

int pthread_attr_setdetachstate_wrapper(pthread_attr_t* attr, int detachstate) {
    return pthread_attr_setdetachstate(attr, detachstate);
}

int pthread_attr_getdetachstate_wrapper(const pthread_attr_t* attr, int* detachstate) {
    return pthread_attr_getdetachstate(attr, detachstate);
}

int pthread_attr_setstacksize_wrapper(pthread_attr_t* attr, size_t stacksize) {
    return pthread_attr_setstacksize(attr, stacksize);
}

int pthread_attr_getstacksize_wrapper(const pthread_attr_t* attr, size_t* stacksize) {
    return pthread_attr_getstacksize(attr, stacksize);
}

/* ============================================================================
 * pthread 杂项包装
 * ============================================================================ */

int pthread_join_wrapper(pthread_t thread, void** retval) {
    return pthread_join(thread, retval);
}

int pthread_detach_wrapper(pthread_t thread) {
    return pthread_detach(thread);
}

pthread_t pthread_self_wrapper(void) {
    return pthread_self();
}

int pthread_equal_wrapper(pthread_t t1, pthread_t t2) {
    return pthread_equal(t1, t2);
}

void pthread_exit_wrapper(void* retval) {
    pthread_exit(retval);
}

int pthread_cancel_wrapper(pthread_t thread) {
    /* Android 不完全支持 pthread_cancel */
    LOG_DEBUG("pthread_cancel_wrapper: 不支持，返回 ENOSYS");
    (void)thread;
    return ENOSYS;
}

int pthread_setcancelstate_wrapper(int state, int* oldstate) {
    /* Android 不完全支持 */
    if (oldstate) *oldstate = PTHREAD_CANCEL_ENABLE;
    (void)state;
    return 0;
}

int pthread_setcanceltype_wrapper(int type, int* oldtype) {
    /* Android 不完全支持 */
    if (oldtype) *oldtype = PTHREAD_CANCEL_DEFERRED;
    (void)type;
    return 0;
}

void pthread_testcancel_wrapper(void) {
    /* No-op - cancellation is not supported on Android */
}
