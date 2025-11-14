#include "netcorehost/hostfxr.hpp"
#include "netcorehost/context.hpp"
#include <stdexcept>
#include <filesystem>
#include <string>
#include <algorithm>
#ifdef __ANDROID__
#include <android/log.h>
#include <sys/stat.h>
#endif

#ifdef _WINDOWS
    #include <windows.h>
    #define LOAD_LIBRARY(path) LoadLibraryW(path)
    #define GET_PROC_ADDRESS GetProcAddress
    #define CLOSE_LIBRARY FreeLibrary
    typedef HMODULE LibraryHandle;
    #define PATH_SEPARATOR L"\\"
    #define EXE_SUFFIX L".exe"
#else
    #include <dlfcn.h>
    // 使用 RTLD_NOW 而不是 RTLD_LAZY，在加载时立即解析所有符号
    // 这样可以避免在 dlsym 时卡住
    #define LOAD_LIBRARY(path) dlopen(path, RTLD_NOW | RTLD_GLOBAL)
    #define GET_PROC_ADDRESS dlsym
    #define CLOSE_LIBRARY dlclose
    typedef void* LibraryHandle;
    #define PATH_SEPARATOR "/"
    #define EXE_SUFFIX ""
#endif

namespace netcorehost {

Hostfxr::Hostfxr(void* library_handle, const PdCString& hostfxr_path)
    : library_handle_(library_handle),
      hostfxr_path_(hostfxr_path),
      dotnet_exe_path_(find_dotnet_exe(hostfxr_path)),
      initialize_for_runtime_config_fn_(nullptr),
      initialize_for_dotnet_command_line_fn_(nullptr),
      get_runtime_delegate_fn_(nullptr),
      run_app_fn_(nullptr),
      close_fn_(nullptr) {
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "Hostfxr constructor: find_dotnet_exe completed");
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "Hostfxr constructor: calling load_functions...");
    #endif
    load_functions();
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "Hostfxr constructor: load_functions completed");
    #endif
}

Hostfxr::~Hostfxr() {
    if (library_handle_) {
        CLOSE_LIBRARY(static_cast<LibraryHandle>(library_handle_));
    }
}

std::shared_ptr<Hostfxr> Hostfxr::load_from_path(const PdCString& path) {
#ifdef _WINDOWS
    LibraryHandle lib = LOAD_LIBRARY(path.c_str());
#else
    LibraryHandle lib = LOAD_LIBRARY(path.c_str());
#endif

    if (!lib) {
#ifdef __ANDROID__
        // 在 Android 上输出 dlerror() 帮助诊断
        const char* dl_error = dlerror();
        __android_log_print(ANDROID_LOG_ERROR, "NetCoreHost",
            "dlopen failed: %s", dl_error ? dl_error : "unknown error");
        __android_log_print(ANDROID_LOG_ERROR, "NetCoreHost",
            "Path: %s", path.c_str());
#endif
        throw HostingException(
            HostingError::CoreHostLibLoadFailure,
            "Failed to load hostfxr library from: " + path.to_string()
        );
    }

    // 使用 new 而不是 make_shared，因为构造函数是私有的
    return std::shared_ptr<Hostfxr>(new Hostfxr(lib, path));
}

void Hostfxr::load_functions() {
    auto lib = static_cast<LibraryHandle>(library_handle_);
    
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "load_functions: getting hostfxr_initialize_for_runtime_config...");
    #endif
    initialize_for_runtime_config_fn_ = 
        reinterpret_cast<bindings::hostfxr_initialize_for_runtime_config_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_initialize_for_runtime_config")
        );
    
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "load_functions: getting hostfxr_initialize_for_dotnet_command_line...");
    #endif
    initialize_for_dotnet_command_line_fn_ = 
        reinterpret_cast<bindings::hostfxr_initialize_for_dotnet_command_line_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_initialize_for_dotnet_command_line")
        );
    
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "load_functions: getting hostfxr_get_runtime_delegate...");
    #endif
    get_runtime_delegate_fn_ = 
        reinterpret_cast<bindings::hostfxr_get_runtime_delegate_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_get_runtime_delegate")
        );
    
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "load_functions: getting hostfxr_run_app...");
    #endif
    run_app_fn_ = 
        reinterpret_cast<bindings::hostfxr_run_app_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_run_app")
        );
    
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "load_functions: getting hostfxr_close...");
    #endif
    close_fn_ = 
        reinterpret_cast<bindings::hostfxr_close_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_close")
        );
    
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "load_functions: validating functions...");
    #endif
    if (!initialize_for_runtime_config_fn_ || !initialize_for_dotnet_command_line_fn_ ||
        !get_runtime_delegate_fn_ || !run_app_fn_ || !close_fn_) {
        throw HostingException(
            HostingError::CoreHostEntryPointFailure,
            "Failed to load required functions from hostfxr library"
        );
    }
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "load_functions: all functions loaded successfully");
    #endif
}

PdCString Hostfxr::find_dotnet_exe(const PdCString& hostfxr_path) {
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "find_dotnet_exe: starting, path=%s", hostfxr_path.to_string().c_str());
    #endif
    
    // 在 Android 上使用纯字符串操作，避免 std::filesystem 的问题
    #ifdef __ANDROID__
    std::string path_str = hostfxr_path.to_string();
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "find_dotnet_exe: converted to string");
    
    // 从路径末尾向上查找，找到包含 "dotnet" 的目录
    size_t pos = path_str.rfind('/');
    std::string dotnet_root;
    
    while (pos != std::string::npos && pos > 0) {
        // 提取当前目录名
        size_t next_slash = path_str.rfind('/', pos - 1);
        size_t dirname_start = (next_slash == std::string::npos) ? 0 : next_slash + 1;
        std::string dirname = path_str.substr(dirname_start, pos - dirname_start);
        
        #ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "find_dotnet_exe: checking dirname=%s", dirname.c_str());
        #endif
        
        // 检查是否是 dotnet 目录（可能是 dotnet、.dotnet 或 dotnet-arm64 等）
        if (dirname == "dotnet" || dirname == ".dotnet" || 
            dirname.find("dotnet") == 0) {
            dotnet_root = path_str.substr(0, pos);
            #ifdef __ANDROID__
            __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "find_dotnet_exe: found dotnet root=%s", dotnet_root.c_str());
            #endif
            break;
        }
        
        if (next_slash == std::string::npos) {
            break;
        }
        pos = next_slash;
    }
    
    if (dotnet_root.empty()) {
        dotnet_root = ".";
        #ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "find_dotnet_exe: dotnet root not found, using current dir");
        #endif
    }
    
    // 在 Android 上，尝试查找 apphost 而不是 dotnet
    // 如果找不到 apphost，就返回 dotnet_root 本身（因为可能不需要实际的可执行文件）
    std::string dotnet_exe;
    #ifdef __ANDROID__
    // 先尝试 apphost
    std::string apphost_path = dotnet_root + "/apphost";
    // 检查文件是否存在（使用 stat）
    struct stat st;
    if (stat(apphost_path.c_str(), &st) == 0 && S_ISREG(st.st_mode)) {
        dotnet_exe = apphost_path;
        __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "find_dotnet_exe: found apphost at %s", dotnet_exe.c_str());
    } else {
        // 如果找不到 apphost，就返回 dotnet_root 本身
        // 因为在 Android 上，我们可能不需要实际的可执行文件路径
        dotnet_exe = dotnet_root;
        __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "find_dotnet_exe: apphost not found, returning dotnet_root: %s", dotnet_exe.c_str());
    }
    #else
    // 非 Android 平台使用 dotnet
    dotnet_exe = dotnet_root + "/dotnet";
    #endif
    
    #ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "NetCoreHost", "find_dotnet_exe: completed, result=%s", dotnet_exe.c_str());
    #endif
    return PdCString(dotnet_exe);
    
    #else
    // 非 Android 平台使用 std::filesystem
    #ifdef _WINDOWS
    std::filesystem::path path(hostfxr_path.to_wstring());
    #else
    std::filesystem::path path(hostfxr_path.to_string());
    #endif
    
    while (path.has_parent_path()) {
        auto dirname = path.parent_path().filename();
        if (dirname == "dotnet" || dirname == ".dotnet") {
            path = path.parent_path();
            break;
        }
        path = path.parent_path();
    }
    
    if (path.empty()) {
        path = std::filesystem::path(".");
    }
    
#ifdef _WINDOWS
    path /= L"dotnet.exe";
    return PdCString(path.wstring());
#else
    path /= "dotnet";
    return PdCString(path.string());
#endif
    #endif
}

std::unique_ptr<HostfxrContextForRuntimeConfig> 
Hostfxr::initialize_for_runtime_config(const PdCString& runtime_config_path) {
    bindings::hostfxr_handle handle = nullptr;
    
    int32_t result = initialize_for_runtime_config_fn_(
        runtime_config_path.c_str(),
        nullptr,
        &handle
    );
    
    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();
    
    bool is_primary = (hosting_result.get_success() == HostingSuccess::Success);
    
    return std::unique_ptr<HostfxrContextForRuntimeConfig>(
        new HostfxrContextForRuntimeConfig(handle, shared_from_this(), is_primary)
    );
}

std::unique_ptr<HostfxrContextForCommandLine> 
Hostfxr::initialize_for_dotnet_command_line(const PdCString& assembly_path) {
    bindings::hostfxr_handle handle = nullptr;
    
    const bindings::char_t* argv[] = { assembly_path.c_str() };
    
    int32_t result = initialize_for_dotnet_command_line_fn_(
        1,
        argv,
        nullptr,
        &handle
    );
    
    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();
    
    bool is_primary = (hosting_result.get_success() == HostingSuccess::Success);
    
    return std::unique_ptr<HostfxrContextForCommandLine>(
        new HostfxrContextForCommandLine(handle, shared_from_this(), is_primary)
    );
}

std::unique_ptr<HostfxrContextForCommandLine> 
Hostfxr::initialize_for_dotnet_command_line_with_dotnet_root(
    const PdCString& assembly_path,
    const PdCString& dotnet_root) {
    bindings::hostfxr_handle handle = nullptr;
    
    const bindings::char_t* argv[] = { assembly_path.c_str() };
    
    // 创建 parameters 结构体
    bindings::hostfxr_initialize_parameters params = 
        bindings::hostfxr_initialize_parameters::with_dotnet_root(dotnet_root.c_str());
    
    int32_t result = initialize_for_dotnet_command_line_fn_(
        1,
        argv,
        &params,
        &handle
    );
    
    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();
    
    bool is_primary = (hosting_result.get_success() == HostingSuccess::Success);
    
    return std::unique_ptr<HostfxrContextForCommandLine>(
        new HostfxrContextForCommandLine(handle, shared_from_this(), is_primary)
    );
}

std::string Hostfxr::get_dotnet_root() const {
    #ifdef __ANDROID__
    // 在 Android 上，如果 dotnet_exe_path_ 就是 dotnet_root（没有可执行文件名），直接返回
    std::string path_str = dotnet_exe_path_.to_string();
    // 检查是否以 /apphost 结尾，如果是则去掉
    if (path_str.length() > 7 && path_str.substr(path_str.length() - 7) == "/apphost") {
        return path_str.substr(0, path_str.length() - 7);
    }
    // 否则直接返回（可能已经是 dotnet_root）
    return path_str;
    #else
    #ifdef _WINDOWS
    std::filesystem::path path(dotnet_exe_path_.to_wstring());
    #else
    std::filesystem::path path(dotnet_exe_path_.to_string());
    #endif
    return path.parent_path().string();
    #endif
}

std::string Hostfxr::get_dotnet_exe() const {
    return dotnet_exe_path_.to_string();
}

} // namespace netcorehost

