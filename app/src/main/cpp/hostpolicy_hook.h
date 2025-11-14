#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#if defined(__aarch64__)
bool PatchHostpolicyStrings();
void InstallHostpolicyHook();
#else
inline bool PatchHostpolicyStrings() { return false; }
inline void InstallHostpolicyHook() {}
#endif

#ifdef __cplusplus
}
#endif

