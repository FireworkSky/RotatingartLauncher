/*
 * glibc-bridge - Math Wrappers
 * 
 * glibc math functions not in Bionic (exp10, complex, etc.)
 */

#define _GNU_SOURCE
#include <math.h>
#include <stdio.h>

#ifdef __ANDROID__
#include <android/log.h>
#define MATH_LOG(...) __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_MATH", __VA_ARGS__)
#else
#define MATH_LOG(...) fprintf(stderr, "[MATH] " __VA_ARGS__)
#endif

/* ============================================================================
 * exp10 family (10^x) - glibc extension, not in Bionic
 * ============================================================================ */

double exp10_wrapper(double x) {
    return pow(10.0, x);
}

float exp10f_wrapper(float x) {
    return powf(10.0f, x);
}

long double exp10l_wrapper(long double x) {
    return powl(10.0L, x);
}

/* ============================================================================
 * pow10 (alias for exp10) - some glibc versions use this name
 * ============================================================================ */

double pow10_wrapper(double x) {
    return pow(10.0, x);
}

float pow10f_wrapper(float x) {
    return powf(10.0f, x);
}

long double pow10l_wrapper(long double x) {
    return powl(10.0L, x);
}

/* ============================================================================
 * Complex Number Functions (cabs, carg, etc.)
 * 
 * These wrappers ensure proper ABI handling for complex numbers.
 * On ARM64, double _Complex is passed as: real in v0, imag in v1
 * On ARM64, double _Complex is passed as: real in d0, imag in d1
 * 
 * The issue is that calling bionic's cabs(double _Complex) via a
 * double (*)(double, double) function pointer may not work correctly
 * due to subtle ABI differences. Using explicit wrappers ensures
 * the complex value is properly constructed.
 * ============================================================================ */

#include <complex.h>

/*
 * cabs_wrapper - Calculate absolute value (modulus) of complex number
 * 
 * ARM64 ABI: real in v0, imag in v1, return in v0
 * ARM64 ABI: real in d0, imag in d1, return in d0
 * 
 * Since we receive two doubles (real, imag), we construct the complex
 * and call the native cabs function.
 */
double cabs_wrapper(double real, double imag) {
    double _Complex z = real + imag * _Complex_I;
    double result = cabs(z);
    MATH_LOG("cabs_wrapper: real=%f, imag=%f, result=%f", real, imag, result);
    return result;
}

/*
 * carg_wrapper - Calculate argument (phase angle) of complex number
 * 
 * Returns the phase angle in radians: atan2(imag, real)
 */
double carg_wrapper(double real, double imag) {
    double _Complex z = real + imag * _Complex_I;
    double result = carg(z);
    MATH_LOG("carg_wrapper: real=%f, imag=%f, result=%f", real, imag, result);
    return result;
}

/*
 * cabsf_wrapper - Float version of cabs
 */
float cabsf_wrapper(float real, float imag) {
    float _Complex z = real + imag * _Complex_I;
    return cabsf(z);
}

/*
 * cargf_wrapper - Float version of carg
 */
float cargf_wrapper(float real, float imag) {
    float _Complex z = real + imag * _Complex_I;
    return cargf(z);
}

/*
 * creal_wrapper - Get real part of complex number
 * Complex numbers are passed as two doubles, so real is first arg
 */
double creal_wrapper(double real, double imag) {
    (void)imag;  /* Unused */
    return real;
}

/*
 * cimag_wrapper - Get imaginary part of complex number
 * Complex numbers are passed as two doubles, so imag is second arg
 */
double cimag_wrapper(double real, double imag) {
    (void)real;  /* Unused */
    return imag;
}

