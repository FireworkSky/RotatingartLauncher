#ifndef NETCOREHOST_BINDINGS_HPP
#define NETCOREHOST_BINDINGS_HPP

#include <cstdint>
#include <cstddef>

#ifdef _WINDOWS
    #define NETHOST_CALLTYPE __cdecl
    #ifdef NETHOST_EXPORT
        #define NETHOST_API __declspec(dllexport)
    #else
        #define NETHOST_API __declspec(dllimport)
    #endif
#else
    #define NETHOST_CALLTYPE
    #define NETHOST_API __attribute__((__visibility__("default")))
#endif

namespace netcorehost {
namespace bindings {

// 平台相关的字符类型
#ifdef _WINDOWS
    typedef wchar_t char_t;
#else
    typedef char char_t;
#endif

// 常量定义
constexpr size_t MAX_PATH = 260;

// 前向声明
typedef void* hostfxr_handle;
typedef void* (*hostfxr_error_writer_fn)(const char_t* message);
typedef void* hostfxr_set_error_writer_fn;

// 状态码枚举
enum class StatusCode : int32_t {
    // 成功代码
    Success = 0,
    Success_HostAlreadyInitialized = 0x00000001,
    Success_DifferentRuntimeProperties = 0x00000002,

    // 错误代码
    InvalidArgFailure = static_cast<int32_t>(0x80008081),
    CoreHostLibLoadFailure = static_cast<int32_t>(0x80008082),
    CoreHostLibMissingFailure = static_cast<int32_t>(0x80008083),
    CoreHostEntryPointFailure = static_cast<int32_t>(0x80008084),
    CoreHostCurHostFindFailure = static_cast<int32_t>(0x80008085),
    CoreClrResolveFailure = static_cast<int32_t>(0x80008087),
    CoreClrBindFailure = static_cast<int32_t>(0x80008088),
    CoreClrInitFailure = static_cast<int32_t>(0x80008089),
    CoreClrExeFailure = static_cast<int32_t>(0x8000808a),
    ResolverInitFailure = static_cast<int32_t>(0x8000808b),
    ResolverResolveFailure = static_cast<int32_t>(0x8000808c),
    LibHostCurExeFindFailure = static_cast<int32_t>(0x8000808d),
    LibHostInitFailure = static_cast<int32_t>(0x8000808e),
    LibHostExecModeFailure = static_cast<int32_t>(0x8000808f),
    LibHostSdkFindFailure = static_cast<int32_t>(0x80008091),
    LibHostInvalidArgs = static_cast<int32_t>(0x80008092),
    InvalidConfigFile = static_cast<int32_t>(0x80008093),
    AppArgNotRunnable = static_cast<int32_t>(0x80008094),
    AppHostExeNotBoundFailure = static_cast<int32_t>(0x80008095),
    FrameworkMissingFailure = static_cast<int32_t>(0x80008096),
    HostApiFailed = static_cast<int32_t>(0x80008097),
    HostApiBufferTooSmall = static_cast<int32_t>(0x80008098),
    LibHostUnknownCommand = static_cast<int32_t>(0x80008099),
    LibHostAppRootFindFailure = static_cast<int32_t>(0x8000809a),
    SdkResolverResolveFailure = static_cast<int32_t>(0x8000809b),
    FrameworkCompatFailure = static_cast<int32_t>(0x8000809c),
    FrameworkCompatRetry = static_cast<int32_t>(0x8000809d),
    AppHostExeNotBundle = static_cast<int32_t>(0x8000809e),
    BundleExtractionFailure = static_cast<int32_t>(0x8000809f),
    BundleExtractionIOError = static_cast<int32_t>(0x800080a0),
    LibHostDuplicateProperty = static_cast<int32_t>(0x800080a1),
    HostApiUnsupportedVersion = static_cast<int32_t>(0x800080a2),
    HostInvalidState = static_cast<int32_t>(0x800080a3),
    HostPropertyNotFound = static_cast<int32_t>(0x800080a4),
    CoreHostIncompatibleConfig = static_cast<int32_t>(0x800080a5),
    HostApiUnsupportedScenario = static_cast<int32_t>(0x800080a6),
    HostFeatureDisabled = static_cast<int32_t>(0x800080a7),
};

// Hostfxr delegate types
enum class hostfxr_delegate_type : int32_t {
    hdt_com_activation,
    hdt_load_in_memory_assembly,
    hdt_winrt_activation,
    hdt_com_register,
    hdt_com_unregister,
    hdt_load_assembly_and_get_function_pointer,
    hdt_get_function_pointer,
    hdt_load_assembly,
    hdt_load_assembly_bytes,
};

// Nethost 结构体
struct get_hostfxr_parameters {
    size_t size;
    const char_t* assembly_path;
    const char_t* dotnet_root;

    static get_hostfxr_parameters with_assembly_path(const char_t* assembly_path) {
        return get_hostfxr_parameters{sizeof(get_hostfxr_parameters), assembly_path, nullptr};
    }

    static get_hostfxr_parameters with_dotnet_root(const char_t* dotnet_root) {
        return get_hostfxr_parameters{sizeof(get_hostfxr_parameters), nullptr, dotnet_root};
    }
};

// Hostfxr initialize parameters 结构体
struct hostfxr_initialize_parameters {
    size_t size;
    const char_t* host_path;
    const char_t* dotnet_root;

    static hostfxr_initialize_parameters with_host_path(const char_t* host_path) {
        return hostfxr_initialize_parameters{sizeof(hostfxr_initialize_parameters), host_path, nullptr};
    }

    static hostfxr_initialize_parameters with_dotnet_root(const char_t* dotnet_root) {
        return hostfxr_initialize_parameters{sizeof(hostfxr_initialize_parameters), nullptr, dotnet_root};
    }
};

// 函数指针类型定义
typedef int32_t (NETHOST_CALLTYPE *get_hostfxr_path_fn)(
    char_t* buffer,
    size_t* buffer_size,
    const get_hostfxr_parameters* parameters);

// 自定义实现的 nethost 函数声明（兼容 libnethost 接口）
extern "C" {
    NETHOST_API int32_t NETHOST_CALLTYPE get_hostfxr_path(
        char_t* buffer,
        size_t* buffer_size,
        const get_hostfxr_parameters* parameters);
}

typedef int32_t (NETHOST_CALLTYPE *hostfxr_initialize_for_dotnet_command_line_fn)(
    int argc,
    const char_t** argv,
    const void* parameters,
    hostfxr_handle* host_context_handle);

typedef int32_t (NETHOST_CALLTYPE *hostfxr_initialize_for_runtime_config_fn)(
    const char_t* runtime_config_path,
    const void* parameters,
    hostfxr_handle* host_context_handle);

typedef int32_t (NETHOST_CALLTYPE *hostfxr_get_runtime_delegate_fn)(
    const hostfxr_handle host_context_handle,
    hostfxr_delegate_type type,
    void** delegate);

typedef int32_t (NETHOST_CALLTYPE *hostfxr_run_app_fn)(
    const hostfxr_handle host_context_handle);

typedef int32_t (NETHOST_CALLTYPE *hostfxr_close_fn)(
    hostfxr_handle host_context_handle);

// Component entry point 签名
typedef int32_t (NETHOST_CALLTYPE *component_entry_point_fn)(void* arg, int32_t arg_size_bytes);

// Load assembly and get function pointer 签名
typedef int32_t (NETHOST_CALLTYPE *load_assembly_and_get_function_pointer_fn)(
    const char_t* assembly_path,
    const char_t* type_name,
    const char_t* method_name,
    const char_t* delegate_type_name,
    void* reserved,
    void** delegate);

// Get function pointer 签名 (netcore 5.0+)
typedef int32_t (NETHOST_CALLTYPE *get_function_pointer_fn)(
    const char_t* type_name,
    const char_t* method_name,
    const char_t* delegate_type_name,
    void* load_context,
    void* reserved,
    void** delegate);

// Load assembly 签名 (netcore 8.0+)
typedef int32_t (NETHOST_CALLTYPE *load_assembly_fn)(
    const char_t* assembly_path,
    void* load_context,
    void* reserved);

// Load assembly bytes 签名 (netcore 8.0+)
typedef int32_t (NETHOST_CALLTYPE *load_assembly_bytes_fn)(
    const uint8_t* assembly,
    size_t assembly_size,
    const uint8_t* symbols,
    size_t symbols_size,
    void* load_context,
    void* reserved);

} // namespace bindings
} // namespace netcorehost

#endif // NETCOREHOST_BINDINGS_HPP

