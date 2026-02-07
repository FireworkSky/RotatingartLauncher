#ifndef DOTNETHOST_ERROR_HPP
#define DOTNETHOST_ERROR_HPP

/**
 * .NET Hosting 错误处理
 */

#include <string>
#include <stdexcept>
#include <optional>
#include "bindings.hpp"

namespace dotnethost {

enum class HostingSuccess {
    Success,
    HostAlreadyInitialized,
    DifferentRuntimeProperties,
    Unknown
};

enum class HostingError {
    InvalidArgFailure,
    CoreHostLibLoadFailure,
    CoreHostLibMissingFailure,
    CoreHostEntryPointFailure,
    CoreHostCurHostFindFailure,
    CoreClrResolveFailure,
    CoreClrBindFailure,
    CoreClrInitFailure,
    CoreClrExeFailure,
    ResolverInitFailure,
    ResolverResolveFailure,
    LibHostCurExeFindFailure,
    LibHostInitFailure,
    LibHostExecModeFailure,
    LibHostSdkFindFailure,
    LibHostInvalidArgs,
    InvalidConfigFile,
    AppArgNotRunnable,
    AppHostExeNotBoundFailure,
    FrameworkMissingFailure,
    HostApiFailed,
    HostApiBufferTooSmall,
    LibHostUnknownCommand,
    LibHostAppRootFindFailure,
    SdkResolverResolveFailure,
    FrameworkCompatFailure,
    FrameworkCompatRetry,
    AppHostExeNotBundle,
    BundleExtractionFailure,
    BundleExtractionIOError,
    LibHostDuplicateProperty,
    HostApiUnsupportedVersion,
    HostInvalidState,
    HostPropertyNotFound,
    CoreHostIncompatibleConfig,
    HostApiUnsupportedScenario,
    HostFeatureDisabled,
    Unknown
};

class HostingResult {
public:
    static HostingResult from_status_code(uint32_t code);
    static HostingResult from_status_code(int32_t code);
    static HostingResult from_success(HostingSuccess success);
    static HostingResult from_error(HostingError error);

    bool is_success() const { return is_success_; }
    bool is_error() const { return !is_success_; }
    uint32_t value() const;
    std::optional<HostingSuccess> get_success() const;
    std::optional<HostingError> get_error() const;
    void throw_if_error() const;
    std::string get_error_message() const;

private:
    HostingResult(bool is_success, HostingSuccess success, HostingError error, uint32_t raw_code)
        : is_success_(is_success), success_(success), error_(error), raw_code_(raw_code) {}

    bool is_success_;
    HostingSuccess success_;
    HostingError error_;
    uint32_t raw_code_;
};

class HostingException : public std::runtime_error {
public:
    explicit HostingException(HostingError error);
    explicit HostingException(HostingError error, const std::string& message);
    HostingError error() const { return error_; }
    uint32_t error_code() const;
private:
    HostingError error_;
};

class AppOrHostingResult {
public:
    explicit AppOrHostingResult(int32_t code) : code_(code) {}
    int32_t value() const { return code_; }
    HostingResult as_hosting_result() const { return HostingResult::from_status_code(code_); }
private:
    int32_t code_;
};

std::string hosting_error_to_string(HostingError error);
std::string hosting_success_to_string(HostingSuccess success);

} // namespace dotnethost

#endif // DOTNETHOST_ERROR_HPP
