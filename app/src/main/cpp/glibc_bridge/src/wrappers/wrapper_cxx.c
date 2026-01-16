/*
 * glibc-bridge - C++ Wrapper Functions
 * 
 * Wrappers for C++ runtime functions that differ between glibc's libstdc++
 * and bionic's libc++. These handle iostream initialization, exception
 * throwing, and other C++ runtime specifics.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <unistd.h>

#include "../include/wrappers.h"
#include "wrapper_path.h"  /* Common wrapper utilities */



static int g_ios_init_count = 0;

void ios_base_Init_ctor_wrapper(void* this_ptr) {
    (void)this_ptr;
    g_ios_init_count++;
    /* bionic's streams are already ready, nothing to do */
}

void ios_base_Init_dtor_wrapper(void* this_ptr) {
    (void)this_ptr;
    g_ios_init_count--;
    if (g_ios_init_count == 0) {
        /* Flush streams on last destruction */
        fflush(stdout);
        fflush(stderr);
    }
}

/* ============================================================================
 * std::terminate
 * ============================================================================ */

void terminate_wrapper(void) {
    write(STDERR_FILENO, "[WRAPPER] std::terminate called\n", 32);
    abort();
}


void throw_logic_error_wrapper(const char* what) {
    wrapper_error_abort("std::logic_error", what);
}

void throw_length_error_wrapper(const char* what) {
    wrapper_error_abort("std::length_error", what);
}

void throw_out_of_range_wrapper(const char* what) {
    wrapper_error_abort("std::out_of_range", what);
}

void throw_out_of_range_fmt_wrapper(const char* fmt, ...) {
    (void)fmt;
    wrapper_error_abort("std::out_of_range", NULL);
}

void throw_invalid_argument_wrapper(const char* what) {
    wrapper_error_abort("std::invalid_argument", what);
}

void throw_bad_cast_wrapper(void) {
    wrapper_error_abort("std::bad_cast", NULL);
}

/* ============================================================================
 * TM/profiling stubs - 这些符号可能被弱引用
 * ============================================================================ */

void __gmon_start___stub(void) {
    /* Profiling stub - no-op */
}

void _ITM_deregisterTMCloneTable_stub(void) {
    /* TM stub - no-op */
}

void _ITM_registerTMCloneTable_stub(void) {
    /* TM stub - no-op */
}

/* ============================================================================
 * LTTng stubs (用于 .NET CoreCLR 跟踪)
 * ============================================================================ */

int lttng_probe_register_stub(void* probe) {
    (void)probe;
    return 0;  /* Success */
}

void lttng_probe_unregister_stub(void* probe) {
    (void)probe;
}

/* ============================================================================
 * Java/GCJ 兼容
 * ============================================================================ */

void _Jv_RegisterClasses_stub(void* classes) {
    (void)classes;
    /* Stub - not used in modern systems */
}

/* CXA 函数定义在 wrapper_process.c 中 */





