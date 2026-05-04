#include "dotnethost/hostfxr.hpp"
#include "dotnethost/context.hpp"
#include <stdexcept>
#include <string>
#include <vector>

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
#else
    #include <dlfcn.h>
    // RTLD_NOW: 加载时立即解析所有符号，避免运行时符号解析卡住
    // RTLD_GLOBAL: 使符号全局可见，供 .NET runtime 内部使用
    #define LOAD_LIBRARY(path) dlopen(path, RTLD_NOW | RTLD_GLOBAL)
    #define GET_PROC_ADDRESS dlsym
    #define CLOSE_LIBRARY dlclose
    typedef void* LibraryHandle;
#endif

#define LOG_TAG "DotNetHost"

namespace dotnethost {

// ========== 路径安全验证 ==========

bool Hostfxr::validate_library_path(const std::string& path) {
    if (path.empty()) return false;

    // 禁止路径遍历
    if (path.find("..") != std::string::npos) {
#ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "Security: path traversal detected in hostfxr path: %s", path.c_str());
#endif
        return false;
    }

    // 验证文件是否存在
#ifdef __ANDROID__
    struct stat st;
    if (stat(path.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "Security: hostfxr path does not exist or is not a regular file: %s", path.c_str());
        return false;
    }
#endif

    return true;
}

// ========== 构造 / 析构 ==========

Hostfxr::Hostfxr(void* library_handle, const PdCString& hostfxr_path)
    : library_handle_(library_handle),
      hostfxr_path_(hostfxr_path),
      initialize_for_dotnet_command_line_fn_(nullptr),
      run_app_fn_(nullptr),
      close_fn_(nullptr),
      set_error_writer_fn_(nullptr) {
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Loading hostfxr functions...");
#endif
    load_functions();
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "hostfxr functions loaded successfully");
#endif
}

Hostfxr::~Hostfxr() {
    if (library_handle_) {
        CLOSE_LIBRARY(static_cast<LibraryHandle>(library_handle_));
        library_handle_ = nullptr;
    }
}

// ========== 加载 ==========

std::shared_ptr<Hostfxr> Hostfxr::load_from_path(const PdCString& path) {
    const std::string path_str = path.to_string();

    // 安全验证
    if (!validate_library_path(path_str)) {
        throw HostingException(
            HostingError::CoreHostLibLoadFailure,
            "Invalid hostfxr library path: " + path_str
        );
    }

    LibraryHandle lib = LOAD_LIBRARY(path.c_str());
    if (!lib) {
#ifdef __ANDROID__
        const char* dl_error = dlerror();
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "dlopen failed for %s: %s", path_str.c_str(), dl_error ? dl_error : "unknown");
#endif
        throw HostingException(
            HostingError::CoreHostLibLoadFailure,
            "Failed to load hostfxr library from: " + path_str
        );
    }

    return std::shared_ptr<Hostfxr>(new Hostfxr(lib, path));
}

void Hostfxr::load_functions() {
    auto lib = static_cast<LibraryHandle>(library_handle_);

    initialize_for_dotnet_command_line_fn_ =
        reinterpret_cast<bindings::hostfxr_initialize_for_dotnet_command_line_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_initialize_for_dotnet_command_line"));

    run_app_fn_ =
        reinterpret_cast<bindings::hostfxr_run_app_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_run_app"));

    close_fn_ =
        reinterpret_cast<bindings::hostfxr_close_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_close"));

    // set_error_writer 是可选的（某些旧版本可能没有）
    set_error_writer_fn_ =
        reinterpret_cast<bindings::hostfxr_set_error_writer_fn>(
            GET_PROC_ADDRESS(lib, "hostfxr_set_error_writer"));

    if (!initialize_for_dotnet_command_line_fn_ || !run_app_fn_ || !close_fn_) {
        throw HostingException(
            HostingError::CoreHostEntryPointFailure,
            "Failed to load required functions from hostfxr library"
        );
    }

#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
        "Functions loaded: init=%p, run=%p, close=%p, error_writer=%p",
        (void*)initialize_for_dotnet_command_line_fn_,
        (void*)run_app_fn_,
        (void*)close_fn_,
        (void*)set_error_writer_fn_);
#endif
}

// ========== 初始化运行时 ==========

std::unique_ptr<HostfxrContextForCommandLine>
Hostfxr::initialize_for_command_line(
    const PdCString& assembly_path,
    int argc,
    const char* const* argv,
    const PdCString& dotnet_root) {

    bindings::hostfxr_handle handle = nullptr;

    // 构建 argv: argv[0] = 程序集路径，argv[1..n] = 用户参数
    std::vector<const bindings::char_t*> full_argv;
    full_argv.reserve(static_cast<size_t>(argc) + 1);
    full_argv.push_back(assembly_path.c_str());
    for (int i = 0; i < argc; i++) {
        if (argv[i] != nullptr) {
            full_argv.push_back(argv[i]);
        }
    }

    bindings::hostfxr_initialize_parameters params =
        bindings::hostfxr_initialize_parameters::with_dotnet_root(dotnet_root.c_str());

    int32_t result = initialize_for_dotnet_command_line_fn_(
        static_cast<int>(full_argv.size()),
        full_argv.data(),
        &params,
        &handle
    );

    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();

    if (!handle) {
        throw HostingException(
            HostingError::HostInvalidState,
            "hostfxr_initialize_for_dotnet_command_line succeeded but returned null handle"
        );
    }

    return std::make_unique<HostfxrContextForCommandLine>(handle, shared_from_this());
}

// ========== 错误输出回调 ==========

bindings::hostfxr_error_writer_fn Hostfxr::set_error_writer(bindings::hostfxr_error_writer_fn writer) {
    if (set_error_writer_fn_) {
        return set_error_writer_fn_(writer);
    }
    return nullptr;
}

} // namespace dotnethost
