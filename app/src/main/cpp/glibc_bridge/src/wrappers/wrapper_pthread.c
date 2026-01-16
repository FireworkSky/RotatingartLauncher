/*
 * glibc-bridge - pthread Wrappers
 * 
 * glibc pthread functions (non-portable, _np suffix) not in Bionic.
 * Includes thread attributes, cancellation stubs, robust mutex stubs.
 */

#define _GNU_SOURCE
#include <pthread.h>
#include <sched.h>
#include <errno.h>
#include <signal.h>

/* ============================================================================
 * pthread_kill - Send signal to a thread
 * ============================================================================ */

int pthread_kill_wrapper(pthread_t thread, int sig) {
    return pthread_kill(thread, sig);
}

int pthread_sigmask_wrapper(int how, const sigset_t* set, sigset_t* oldset) {
    return pthread_sigmask(how, set, oldset);
}

/* ============================================================================
 * Default thread attributes
 * ============================================================================ */

int pthread_setattr_default_np_wrapper(const pthread_attr_t* attr) {
    (void)attr;
    /* Stub - not available on Android, just succeed silently */
    return 0;
}

int pthread_getattr_default_np_wrapper(pthread_attr_t* attr) {
    /* Initialize with default values */
    return pthread_attr_init(attr);
}

/* ============================================================================
 * CPU affinity for thread attributes
 * ============================================================================ */

int pthread_attr_setaffinity_np_wrapper(pthread_attr_t* attr, 
                                        size_t cpusetsize, 
                                        const cpu_set_t* cpuset) {
    (void)attr;
    (void)cpusetsize;
    (void)cpuset;
    /* Stub - Android doesn't support this in attr, use sched_setaffinity instead */
    return 0;
}

int pthread_attr_getaffinity_np_wrapper(const pthread_attr_t* attr, 
                                        size_t cpusetsize, 
                                        cpu_set_t* cpuset) {
    (void)attr;
    (void)cpusetsize;
    if (cpuset) {
        CPU_ZERO(cpuset);
        CPU_SET(0, cpuset);
    }
    return 0;
}

/* ============================================================================
 * Thread cleanup (old-style)
 * ============================================================================ */

void pthread_cleanup_push_wrapper(void* routine, void* arg) {
    (void)routine;
    (void)arg;
    /* Note: Real cleanup should use pthread_cleanup_push macro */
}

void pthread_cleanup_pop_wrapper(int execute) {
    (void)execute;
    /* Note: Real cleanup should use pthread_cleanup_pop macro */
}

/* ============================================================================
 * Robust mutex (not supported on all Android versions)
 * ============================================================================ */

int pthread_mutexattr_setrobust_wrapper(pthread_mutexattr_t* attr, int robustness) {
    (void)attr;
    (void)robustness;
    /* Stub - robust mutexes not available on Android */
    return 0;
}

int pthread_mutexattr_getrobust_wrapper(const pthread_mutexattr_t* attr, int* robustness) {
    (void)attr;
    if (robustness) *robustness = 0; /* PTHREAD_MUTEX_STALLED */
    return 0;
}

int pthread_mutexattr_setprioceiling_wrapper(pthread_mutexattr_t* attr, int prioceiling) {
    (void)attr;
    (void)prioceiling;
    /* Stub - priority ceiling not available on Android */
    return 0;
}

int pthread_mutexattr_getprioceiling_wrapper(const pthread_mutexattr_t* attr, int* prioceiling) {
    (void)attr;
    if (prioceiling) *prioceiling = 0;
    return 0;
}

int pthread_mutex_consistent_wrapper(pthread_mutex_t* mutex) {
    (void)mutex;
    /* pthread_mutex_consistent is used to mark a robust mutex as consistent
     * after it was left in an inconsistent state by a dead owner.
     * On Android/Bionic, robust mutexes are not fully supported.
     * Since we already stub out robust mutex attributes, we can just
     * return success here to allow programs to call it without crashing. */
    return 0;
}

/* ============================================================================
 * pthread cancellation (NOT supported on Android/Bionic)
 * These are stubs that make programs think cancellation is disabled.
 * ============================================================================ */

/* POSIX cancellation states */
#ifndef PTHREAD_CANCEL_ENABLE
#define PTHREAD_CANCEL_ENABLE  0
#define PTHREAD_CANCEL_DISABLE 1
#endif

#ifndef PTHREAD_CANCEL_DEFERRED
#define PTHREAD_CANCEL_DEFERRED     0
#define PTHREAD_CANCEL_ASYNCHRONOUS 1
#endif

/* pthread_cancel, pthread_setcancelstate, pthread_setcanceltype 定义在 wrapper_thread.c 中 */

/* ============================================================================
 * pthread cancellation internals
 * ============================================================================ */

void pthread_register_cancel_wrapper(void* buf) {
    (void)buf;
    /* Stub - Android doesn't support pthread cancellation well */
}

void pthread_unregister_cancel_wrapper(void* buf) {
    (void)buf;
    /* Stub */
}

void pthread_unwind_next_wrapper(void* buf) {
    (void)buf;
    /* Stub - this is internal to glibc's cancellation */
}

