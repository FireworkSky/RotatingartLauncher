/*
 * glibc-bridge - 时间相关函数包装
 * 
 * 包含 clock_gettime, nanosleep, select, pselect 等时间相关函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <errno.h>
#include <unistd.h>
#include <sys/select.h>
#include <sys/time.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* ============================================================================
 * clock_gettime 包装
 * struct timespec 在 64 位系统上 glibc 和 bionic 兼容
 * ============================================================================ */

int clock_gettime_wrapper(clockid_t clk_id, struct timespec *tp) {
    /* 大多数 clock ID 相同: CLOCK_REALTIME=0, CLOCK_MONOTONIC=1 */
    return clock_gettime(clk_id, tp);
}

/* ============================================================================
 * clock_settime 包装
 * ============================================================================ */

int clock_settime_wrapper(clockid_t clk_id, const struct timespec *tp) {
    return clock_settime(clk_id, tp);
}

/* ============================================================================
 * clock_getres 包装
 * ============================================================================ */

int clock_getres_wrapper(clockid_t clk_id, struct timespec *res) {
    return clock_getres(clk_id, res);
}

/* ============================================================================
 * nanosleep 包装
 * ============================================================================ */

int nanosleep_wrapper(const struct timespec *req, struct timespec *rem) {
    return nanosleep(req, rem);
}

/* ============================================================================
 * clock_nanosleep 包装
 * ============================================================================ */

int clock_nanosleep_wrapper(clockid_t clk_id, int flags,
                             const struct timespec *request,
                             struct timespec *remain) {
    return clock_nanosleep(clk_id, flags, request, remain);
}

/* ============================================================================
 * gettimeofday 包装
 * ============================================================================ */

int gettimeofday_wrapper(struct timeval *tv, void *tz) {
    return gettimeofday(tv, tz);
}

/* ============================================================================
 * settimeofday 包装
 * ============================================================================ */

int settimeofday_wrapper(const struct timeval *tv, const void *tz) {
    return settimeofday(tv, tz);
}

/* ============================================================================
 * time 包装
 * ============================================================================ */

time_t time_wrapper(time_t *tloc) {
    return time(tloc);
}

/* ============================================================================
 * localtime / gmtime 包装
 * 使用 void* 以匹配头文件声明
 * ============================================================================ */

void *localtime_wrapper(const void *timer) {
    return localtime((const time_t*)timer);
}

void *localtime_r_wrapper(const void *timer, void *result) {
    return localtime_r((const time_t*)timer, (struct tm*)result);
}

void *gmtime_wrapper(const void *timer) {
    return gmtime((const time_t*)timer);
}

void *gmtime_r_wrapper(const void *timer, void *result) {
    return gmtime_r((const time_t*)timer, (struct tm*)result);
}

/* ============================================================================
 * mktime / timegm 包装
 * ============================================================================ */

time_t mktime_wrapper(struct tm *tm) {
    return mktime(tm);
}

time_t timegm_wrapper(struct tm *tm) {
    return timegm(tm);
}

/* ============================================================================
 * strftime 包装
 * ============================================================================ */

size_t strftime_wrapper(char *s, size_t max, const char *format,
                         const void *tm) {
    return strftime(s, max, format, (const struct tm*)tm);
}

/* ============================================================================
 * strptime 包装
 * ============================================================================ */

char *strptime_wrapper(const char *s, const char *format, struct tm *tm) {
    return strptime(s, format, tm);
}

/* ============================================================================
 * select 包装
 * 
 * 处理 Android 上 stdin 可能无效的问题
 * ============================================================================ */

int select_wrapper(int nfds, fd_set *readfds, fd_set *writefds,
                   fd_set *exceptfds, struct timeval *timeout) {
    LOG_DEBUG("select_wrapper: nfds=%d", nfds);
    
    int result = select(nfds, readfds, writefds, exceptfds, timeout);
    
    /* 处理 Android 上的 stdin 无效问题 */
    if (result < 0 && errno == EBADF) {
        if (readfds) FD_ZERO(readfds);
        if (writefds) FD_ZERO(writefds);
        if (exceptfds) FD_ZERO(exceptfds);
        return 0;
    }
    
    return result;
}

/* ============================================================================
 * pselect 包装
 * 
 * sigset_t 大小在 bionic 上匹配 (8 字节)
 * ============================================================================ */

int pselect_wrapper(int nfds, fd_set *readfds, fd_set *writefds,
                    fd_set *exceptfds, const struct timespec *timeout,
                    const sigset_t *sigmask) {
    WRAPPER_BEGIN("pselect");
    int ret = pselect(nfds, readfds, writefds, exceptfds, timeout, sigmask);
    WRAPPER_RETURN(ret);
}

/* ============================================================================
 * poll / ppoll 包装
 * ============================================================================ */

#include <poll.h>

int poll_wrapper(struct pollfd *fds, nfds_t nfds, int timeout) {
    return poll(fds, nfds, timeout);
}

int ppoll_wrapper(struct pollfd *fds, nfds_t nfds,
                  const struct timespec *tmo_p, const sigset_t *sigmask) {
    return ppoll(fds, nfds, tmo_p, sigmask);
}

/* ============================================================================
 * usleep / sleep 包装
 * ============================================================================ */

int usleep_wrapper(useconds_t usec) {
    return usleep(usec);
}

unsigned int sleep_wrapper(unsigned int seconds) {
    return sleep(seconds);
}

/* ============================================================================
 * alarm 包装
 * ============================================================================ */

unsigned int alarm_wrapper(unsigned int seconds) {
    return alarm(seconds);
}

/* ============================================================================
 * timer 系列包装
 * ============================================================================ */

int timer_create_wrapper(clockid_t clockid, struct sigevent *sevp,
                          timer_t *timerid) {
    return timer_create(clockid, sevp, timerid);
}

int timer_delete_wrapper(timer_t timerid) {
    return timer_delete(timerid);
}

int timer_settime_wrapper(timer_t timerid, int flags,
                           const struct itimerspec *new_value,
                           struct itimerspec *old_value) {
    return timer_settime(timerid, flags, new_value, old_value);
}

int timer_gettime_wrapper(timer_t timerid, struct itimerspec *curr_value) {
    return timer_gettime(timerid, curr_value);
}

int timer_getoverrun_wrapper(timer_t timerid) {
    return timer_getoverrun(timerid);
}

/* ============================================================================
 * setitimer / getitimer 包装
 * ============================================================================ */

int setitimer_wrapper(int which, const struct itimerval *new_value,
                       struct itimerval *old_value) {
    return setitimer(which, new_value, old_value);
}

int getitimer_wrapper(int which, struct itimerval *curr_value) {
    return getitimer(which, curr_value);
}
