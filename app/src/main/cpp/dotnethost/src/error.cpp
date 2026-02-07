#include "dotnethost/error.hpp"

namespace dotnethost {

HostingResult HostingResult::from_status_code(uint32_t code) {
    if (static_cast<int32_t>(code) >= 0) {
        HostingSuccess success;
        switch (static_cast<bindings::StatusCode>(code)) {
            case bindings::StatusCode::Success:
                success = HostingSuccess::Success; break;
            case bindings::StatusCode::Success_HostAlreadyInitialized:
                success = HostingSuccess::HostAlreadyInitialized; break;
            case bindings::StatusCode::Success_DifferentRuntimeProperties:
                success = HostingSuccess::DifferentRuntimeProperties; break;
            default:
                success = HostingSuccess::Unknown; break;
        }
        return HostingResult(true, success, HostingError::Unknown, code);
    } else {
        HostingError error;
        switch (static_cast<bindings::StatusCode>(code)) {
            case bindings::StatusCode::InvalidArgFailure:          error = HostingError::InvalidArgFailure; break;
            case bindings::StatusCode::CoreHostLibLoadFailure:     error = HostingError::CoreHostLibLoadFailure; break;
            case bindings::StatusCode::CoreHostLibMissingFailure:  error = HostingError::CoreHostLibMissingFailure; break;
            case bindings::StatusCode::CoreHostEntryPointFailure:  error = HostingError::CoreHostEntryPointFailure; break;
            case bindings::StatusCode::CoreHostCurHostFindFailure: error = HostingError::CoreHostCurHostFindFailure; break;
            case bindings::StatusCode::CoreClrResolveFailure:      error = HostingError::CoreClrResolveFailure; break;
            case bindings::StatusCode::CoreClrBindFailure:         error = HostingError::CoreClrBindFailure; break;
            case bindings::StatusCode::CoreClrInitFailure:         error = HostingError::CoreClrInitFailure; break;
            case bindings::StatusCode::CoreClrExeFailure:          error = HostingError::CoreClrExeFailure; break;
            case bindings::StatusCode::ResolverInitFailure:        error = HostingError::ResolverInitFailure; break;
            case bindings::StatusCode::ResolverResolveFailure:     error = HostingError::ResolverResolveFailure; break;
            case bindings::StatusCode::LibHostCurExeFindFailure:   error = HostingError::LibHostCurExeFindFailure; break;
            case bindings::StatusCode::LibHostInitFailure:         error = HostingError::LibHostInitFailure; break;
            case bindings::StatusCode::LibHostExecModeFailure:     error = HostingError::LibHostExecModeFailure; break;
            case bindings::StatusCode::LibHostSdkFindFailure:      error = HostingError::LibHostSdkFindFailure; break;
            case bindings::StatusCode::LibHostInvalidArgs:         error = HostingError::LibHostInvalidArgs; break;
            case bindings::StatusCode::InvalidConfigFile:          error = HostingError::InvalidConfigFile; break;
            case bindings::StatusCode::AppArgNotRunnable:          error = HostingError::AppArgNotRunnable; break;
            case bindings::StatusCode::AppHostExeNotBoundFailure:  error = HostingError::AppHostExeNotBoundFailure; break;
            case bindings::StatusCode::FrameworkMissingFailure:    error = HostingError::FrameworkMissingFailure; break;
            case bindings::StatusCode::HostApiFailed:              error = HostingError::HostApiFailed; break;
            case bindings::StatusCode::HostApiBufferTooSmall:      error = HostingError::HostApiBufferTooSmall; break;
            case bindings::StatusCode::LibHostUnknownCommand:      error = HostingError::LibHostUnknownCommand; break;
            case bindings::StatusCode::LibHostAppRootFindFailure:  error = HostingError::LibHostAppRootFindFailure; break;
            case bindings::StatusCode::SdkResolverResolveFailure:  error = HostingError::SdkResolverResolveFailure; break;
            case bindings::StatusCode::FrameworkCompatFailure:     error = HostingError::FrameworkCompatFailure; break;
            case bindings::StatusCode::FrameworkCompatRetry:       error = HostingError::FrameworkCompatRetry; break;
            case bindings::StatusCode::AppHostExeNotBundle:        error = HostingError::AppHostExeNotBundle; break;
            case bindings::StatusCode::BundleExtractionFailure:    error = HostingError::BundleExtractionFailure; break;
            case bindings::StatusCode::BundleExtractionIOError:    error = HostingError::BundleExtractionIOError; break;
            case bindings::StatusCode::LibHostDuplicateProperty:   error = HostingError::LibHostDuplicateProperty; break;
            case bindings::StatusCode::HostApiUnsupportedVersion:  error = HostingError::HostApiUnsupportedVersion; break;
            case bindings::StatusCode::HostInvalidState:           error = HostingError::HostInvalidState; break;
            case bindings::StatusCode::HostPropertyNotFound:       error = HostingError::HostPropertyNotFound; break;
            case bindings::StatusCode::CoreHostIncompatibleConfig: error = HostingError::CoreHostIncompatibleConfig; break;
            case bindings::StatusCode::HostApiUnsupportedScenario: error = HostingError::HostApiUnsupportedScenario; break;
            case bindings::StatusCode::HostFeatureDisabled:        error = HostingError::HostFeatureDisabled; break;
            default:                                               error = HostingError::Unknown; break;
        }
        return HostingResult(false, HostingSuccess::Unknown, error, code);
    }
}

HostingResult HostingResult::from_status_code(int32_t code) {
    return from_status_code(static_cast<uint32_t>(code));
}

HostingResult HostingResult::from_success(HostingSuccess success) {
    uint32_t code = 0;
    switch (success) {
        case HostingSuccess::Success:
            code = static_cast<uint32_t>(bindings::StatusCode::Success); break;
        case HostingSuccess::HostAlreadyInitialized:
            code = static_cast<uint32_t>(bindings::StatusCode::Success_HostAlreadyInitialized); break;
        case HostingSuccess::DifferentRuntimeProperties:
            code = static_cast<uint32_t>(bindings::StatusCode::Success_DifferentRuntimeProperties); break;
        default:
            code = 0; break;
    }
    return HostingResult(true, success, HostingError::Unknown, code);
}

HostingResult HostingResult::from_error(HostingError error) {
    uint32_t code = static_cast<uint32_t>(bindings::StatusCode::InvalidArgFailure);
    switch (error) {
        case HostingError::InvalidArgFailure:          code = static_cast<uint32_t>(bindings::StatusCode::InvalidArgFailure); break;
        case HostingError::CoreHostLibLoadFailure:     code = static_cast<uint32_t>(bindings::StatusCode::CoreHostLibLoadFailure); break;
        case HostingError::CoreHostLibMissingFailure:  code = static_cast<uint32_t>(bindings::StatusCode::CoreHostLibMissingFailure); break;
        case HostingError::CoreHostEntryPointFailure:  code = static_cast<uint32_t>(bindings::StatusCode::CoreHostEntryPointFailure); break;
        case HostingError::FrameworkMissingFailure:    code = static_cast<uint32_t>(bindings::StatusCode::FrameworkMissingFailure); break;
        default: break;
    }
    return HostingResult(false, HostingSuccess::Unknown, error, code);
}

uint32_t HostingResult::value() const { return raw_code_; }

std::optional<HostingSuccess> HostingResult::get_success() const {
    if (is_success_) return success_;
    return std::nullopt;
}

std::optional<HostingError> HostingResult::get_error() const {
    if (!is_success_) return error_;
    return std::nullopt;
}

void HostingResult::throw_if_error() const {
    if (!is_success_) throw HostingException(error_);
}

std::string HostingResult::get_error_message() const {
    return is_success_ ? hosting_success_to_string(success_) : hosting_error_to_string(error_);
}

// HostingException

HostingException::HostingException(HostingError error)
    : std::runtime_error(hosting_error_to_string(error)), error_(error) {}

HostingException::HostingException(HostingError error, const std::string& message)
    : std::runtime_error(message), error_(error) {}

uint32_t HostingException::error_code() const {
    return HostingResult::from_error(error_).value();
}

// 错误码描述

std::string hosting_error_to_string(HostingError error) {
    switch (error) {
        case HostingError::InvalidArgFailure:          return "One of the specified arguments for the operation is invalid";
        case HostingError::CoreHostLibLoadFailure:     return "There was a failure loading a dependent library";
        case HostingError::CoreHostLibMissingFailure:  return "One of the dependent libraries is missing";
        case HostingError::CoreHostEntryPointFailure:  return "One of the dependent libraries is missing a required entry point";
        case HostingError::CoreHostCurHostFindFailure: return "The location is not in the right place relative to other expected components";
        case HostingError::CoreClrResolveFailure:      return "The coreclr library could not be found";
        case HostingError::CoreClrBindFailure:         return "The loaded coreclr library doesn't have one of the required entry points";
        case HostingError::CoreClrInitFailure:         return "The call to coreclr_initialize failed";
        case HostingError::CoreClrExeFailure:          return "The call to coreclr_execute_assembly failed";
        case HostingError::ResolverInitFailure:        return "Initialization of the hostpolicy dependency resolver failed";
        case HostingError::ResolverResolveFailure:     return "Resolution of dependencies in hostpolicy failed";
        case HostingError::LibHostCurExeFindFailure:   return "Failure to determine the location of the current executable";
        case HostingError::LibHostInitFailure:         return "Initialization of the hostpolicy library failed";
        case HostingError::LibHostSdkFindFailure:      return "Failure to find the requested SDK";
        case HostingError::LibHostInvalidArgs:         return "Arguments to hostpolicy are invalid";
        case HostingError::InvalidConfigFile:          return "The .runtimeconfig.json file is invalid";
        case HostingError::AppHostExeNotBoundFailure:  return "apphost failed to determine which application to run";
        case HostingError::FrameworkMissingFailure:    return "It was not possible to find a compatible framework version";
        case HostingError::HostApiFailed:              return "hostpolicy could not calculate the NATIVE_DLL_SEARCH_DIRECTORIES";
        case HostingError::HostApiBufferTooSmall:      return "The buffer specified to an API is not big enough to fit the requested value";
        case HostingError::LibHostUnknownCommand:      return "corehost_main_with_output_buffer was called with an unsupported host command";
        case HostingError::LibHostAppRootFindFailure:  return "The imprinted application path doesn't exist";
        case HostingError::SdkResolverResolveFailure:  return "hostfxr_resolve_sdk2 failed to find a matching SDK";
        case HostingError::FrameworkCompatFailure:     return "There were two framework references to the same framework which were not compatible";
        case HostingError::AppHostExeNotBundle:        return "Error reading the bundle footer metadata from a single-file apphost";
        case HostingError::BundleExtractionFailure:    return "Error extracting single-file apphost bundle";
        case HostingError::BundleExtractionIOError:    return "Error reading or writing files during single-file apphost bundle extraction";
        case HostingError::LibHostDuplicateProperty:   return "The .runtimeconfig.json contains a runtime property which is also produced by the hosting layer";
        case HostingError::HostApiUnsupportedVersion:  return "Feature which requires certain version of the hosting layer binaries was used on a version which doesn't support it";
        case HostingError::HostInvalidState:           return "The current state is incompatible with the requested operation";
        case HostingError::HostPropertyNotFound:       return "Property requested by hostfxr_get_runtime_property_value doesn't exist";
        case HostingError::CoreHostIncompatibleConfig: return "The component being initialized requires framework which is not available or incompatible";
        case HostingError::HostApiUnsupportedScenario: return "Requesting the given delegate type using the given context is currently not supported";
        case HostingError::HostFeatureDisabled:        return "Managed feature support for native hosting is disabled";
        default:                                       return "Unknown hosting error";
    }
}

std::string hosting_success_to_string(HostingSuccess success) {
    switch (success) {
        case HostingSuccess::Success:                    return "Operation was successful";
        case HostingSuccess::HostAlreadyInitialized:     return "Host already initialized";
        case HostingSuccess::DifferentRuntimeProperties: return "Different runtime properties";
        default:                                         return "Unknown success code";
    }
}

} // namespace dotnethost
