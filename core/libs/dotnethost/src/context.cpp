#include "dotnethost/context.hpp"
#include "dotnethost/hostfxr.hpp"

namespace dotnethost {

HostfxrContextForCommandLine::HostfxrContextForCommandLine(
    bindings::hostfxr_handle handle,
    std::shared_ptr<Hostfxr> hostfxr
) : handle_(handle), hostfxr_(std::move(hostfxr)), closed_(false) {}

HostfxrContextForCommandLine::~HostfxrContextForCommandLine() {
    if (!closed_ && handle_) {
        try { close(); } catch (...) {}
    }
}

HostfxrContextForCommandLine::HostfxrContextForCommandLine(HostfxrContextForCommandLine&& other) noexcept
    : handle_(other.handle_), hostfxr_(std::move(other.hostfxr_)), closed_(other.closed_) {
    other.handle_ = nullptr;
    other.closed_ = true;
}

HostfxrContextForCommandLine& HostfxrContextForCommandLine::operator=(HostfxrContextForCommandLine&& other) noexcept {
    if (this != &other) {
        if (!closed_ && handle_) {
            try { close(); } catch (...) {}
        }
        handle_ = other.handle_;
        hostfxr_ = std::move(other.hostfxr_);
        closed_ = other.closed_;
        other.handle_ = nullptr;
        other.closed_ = true;
    }
    return *this;
}

AppOrHostingResult HostfxrContextForCommandLine::run_app() {
    if (closed_ || !handle_) {
        throw HostingException(
            HostingError::HostInvalidState,
            "Cannot run app: context is closed or handle is null"
        );
    }
    int32_t result = hostfxr_->get_run_app_fn()(handle_);
    return AppOrHostingResult(result);
}

void HostfxrContextForCommandLine::close() {
    if (!closed_ && handle_) {
        int32_t result = hostfxr_->get_close_fn()(handle_);
        auto hosting_result = HostingResult::from_status_code(result);
        hosting_result.throw_if_error();
        handle_ = nullptr;
        closed_ = true;
    }
}

} // namespace dotnethost
