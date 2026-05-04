#ifndef DOTNETHOST_BINDINGS_HPP
#define DOTNETHOST_BINDINGS_HPP

/**
 * .NET Hosting API 原生绑定
 * 参照 dotnet/runtime hostfxr.h 定义
 * 仅保留项目实际使用的 API
 */

#include <cstdint>
#include <cstddef>

#ifdef _WINDOWS
    #define DOTNETHOST_CALLTYPE __cdecl
    #ifdef DOTNETHOST_EXPORT
        #define DOTNETHOST_API __declspec(dllexport)
    #else
        #define DOTNETHOST_API __declspec(dllimport)
    #endif
#else
    #define DOTNETHOST_CALLTYPE
    #define DOTNETHOST_API __attribute__((__visibility__("default")))
#endif

namespace dotnethost {
namespace bindings {

// 平台字符类型 (Windows: wchar_t / 其他: char)
#ifdef _WINDOWS
    typedef wchar_t char_t;
#else
    typedef char char_t;
#endif

constexpr size_t MAX_PATH = 260;

// ========== hostfxr 类型定义（对齐官方 hostfxr.h）==========

typedef void* hostfxr_handle;

// 错误输出回调（用于捕获 hostfxr 内部错误消息）
typedef void(DOTNETHOST_CALLTYPE *hostfxr_error_writer_fn)(const char_t *message);
typedef hostfxr_error_writer_fn(DOTNETHOST_CALLTYPE *hostfxr_set_error_writer_fn)(hostfxr_error_writer_fn error_writer);

// 状态码（对齐 dotnet/runtime error_codes.h）
enum class StatusCode : int32_t {
    Success = 0,
    Success_HostAlreadyInitialized = 0x00000001,
    Success_DifferentRuntimeProperties = 0x00000002,

    InvalidArgFailure             = static_cast<int32_t>(0x80008081),
    CoreHostLibLoadFailure        = static_cast<int32_t>(0x80008082),
    CoreHostLibMissingFailure     = static_cast<int32_t>(0x80008083),
    CoreHostEntryPointFailure     = static_cast<int32_t>(0x80008084),
    CoreHostCurHostFindFailure    = static_cast<int32_t>(0x80008085),
    CoreClrResolveFailure         = static_cast<int32_t>(0x80008087),
    CoreClrBindFailure            = static_cast<int32_t>(0x80008088),
    CoreClrInitFailure            = static_cast<int32_t>(0x80008089),
    CoreClrExeFailure             = static_cast<int32_t>(0x8000808a),
    ResolverInitFailure           = static_cast<int32_t>(0x8000808b),
    ResolverResolveFailure        = static_cast<int32_t>(0x8000808c),
    LibHostCurExeFindFailure      = static_cast<int32_t>(0x8000808d),
    LibHostInitFailure            = static_cast<int32_t>(0x8000808e),
    LibHostExecModeFailure        = static_cast<int32_t>(0x8000808f),
    LibHostSdkFindFailure         = static_cast<int32_t>(0x80008091),
    LibHostInvalidArgs            = static_cast<int32_t>(0x80008092),
    InvalidConfigFile             = static_cast<int32_t>(0x80008093),
    AppArgNotRunnable             = static_cast<int32_t>(0x80008094),
    AppHostExeNotBoundFailure     = static_cast<int32_t>(0x80008095),
    FrameworkMissingFailure       = static_cast<int32_t>(0x80008096),
    HostApiFailed                 = static_cast<int32_t>(0x80008097),
    HostApiBufferTooSmall         = static_cast<int32_t>(0x80008098),
    LibHostUnknownCommand         = static_cast<int32_t>(0x80008099),
    LibHostAppRootFindFailure     = static_cast<int32_t>(0x8000809a),
    SdkResolverResolveFailure     = static_cast<int32_t>(0x8000809b),
    FrameworkCompatFailure        = static_cast<int32_t>(0x8000809c),
    FrameworkCompatRetry          = static_cast<int32_t>(0x8000809d),
    AppHostExeNotBundle           = static_cast<int32_t>(0x8000809e),
    BundleExtractionFailure       = static_cast<int32_t>(0x8000809f),
    BundleExtractionIOError       = static_cast<int32_t>(0x800080a0),
    LibHostDuplicateProperty      = static_cast<int32_t>(0x800080a1),
    HostApiUnsupportedVersion     = static_cast<int32_t>(0x800080a2),
    HostInvalidState              = static_cast<int32_t>(0x800080a3),
    HostPropertyNotFound          = static_cast<int32_t>(0x800080a4),
    CoreHostIncompatibleConfig    = static_cast<int32_t>(0x800080a5),
    HostApiUnsupportedScenario    = static_cast<int32_t>(0x800080a6),
    HostFeatureDisabled           = static_cast<int32_t>(0x800080a7),
};

// ========== nethost 结构体 ==========

struct get_hostfxr_parameters {
    size_t size;
    const char_t* assembly_path;
    const char_t* dotnet_root;
};

// hostfxr 初始化参数（对齐官方 hostfxr_initialize_parameters）
struct hostfxr_initialize_parameters {
    size_t size;
    const char_t* host_path;
    const char_t* dotnet_root;

    static hostfxr_initialize_parameters with_dotnet_root(const char_t* dotnet_root) {
        return hostfxr_initialize_parameters{sizeof(hostfxr_initialize_parameters), nullptr, dotnet_root};
    }
};

// ========== 函数指针类型 ==========

// nethost: 定位 hostfxr 库路径
typedef int32_t (DOTNETHOST_CALLTYPE *get_hostfxr_path_fn)(
    char_t* buffer, size_t* buffer_size,
    const get_hostfxr_parameters* parameters);

// 自定义 nethost 实现（静态链接）
extern "C" {
    DOTNETHOST_API int32_t DOTNETHOST_CALLTYPE get_hostfxr_path(
        char_t* buffer, size_t* buffer_size,
        const get_hostfxr_parameters* parameters);
}

// hostfxr_initialize_for_dotnet_command_line
typedef int32_t (DOTNETHOST_CALLTYPE *hostfxr_initialize_for_dotnet_command_line_fn)(
    int argc, const char_t** argv,
    const void* parameters,
    hostfxr_handle* host_context_handle);

// hostfxr_run_app
typedef int32_t (DOTNETHOST_CALLTYPE *hostfxr_run_app_fn)(
    const hostfxr_handle host_context_handle);

// hostfxr_close
typedef int32_t (DOTNETHOST_CALLTYPE *hostfxr_close_fn)(
    hostfxr_handle host_context_handle);

} // namespace bindings
} // namespace dotnethost

#endif // DOTNETHOST_BINDINGS_HPP
