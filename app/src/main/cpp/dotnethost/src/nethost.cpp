#include "dotnethost/nethost.hpp"
#include "dotnethost/hostfxr.hpp"
#include <vector>
#include <cstring>

namespace dotnethost {

bindings::get_hostfxr_path_fn Nethost::load_nethost_function() {
    // 直接返回静态链接的函数指针
    return &bindings::get_hostfxr_path;
}

PdCString Nethost::get_hostfxr_path_internal(const bindings::get_hostfxr_parameters* parameters) {
    auto get_hostfxr_path_func = load_nethost_function();

    std::vector<bindings::char_t> buffer(bindings::MAX_PATH);
    size_t buffer_size = buffer.size();

    int32_t result = get_hostfxr_path_func(buffer.data(), &buffer_size, parameters);

    if (result == static_cast<int32_t>(bindings::StatusCode::HostApiBufferTooSmall)) {
        buffer.resize(buffer_size);
        result = get_hostfxr_path_func(buffer.data(), &buffer_size, parameters);
    }

    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();

#ifdef _WINDOWS
    return PdCString(std::wstring(buffer.data(), buffer_size - 1));
#else
    return PdCString(std::string(buffer.data(), buffer_size - 1));
#endif
}

PdCString Nethost::get_hostfxr_path() {
    return get_hostfxr_path_internal(nullptr);
}

std::shared_ptr<Hostfxr> Nethost::load_hostfxr() {
    auto hostfxr_path = get_hostfxr_path();
    return Hostfxr::load_from_path(hostfxr_path);
}

} // namespace dotnethost
