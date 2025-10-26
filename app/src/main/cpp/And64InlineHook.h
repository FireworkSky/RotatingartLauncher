
#include <stdlib.h>

#pragma once
#define A64_MAX_BACKUPS 256

#ifdef __cplusplus
extern "C" {
#endif

    void A64HookFunction(void *const symbol, void *const replace, void **result);
    void *A64HookFunctionV(void *const symbol, void *const replace,
                           void *const rwx, const uintptr_t rwx_size);

#ifdef __cplusplus
}
#endif