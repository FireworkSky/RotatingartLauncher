#ifndef DOTNETHOST_NETHOST_HPP
#define DOTNETHOST_NETHOST_HPP

/**
 * Nethost API - 定位和加载 hostfxr 库
 * 仅保留实际使用的 load_hostfxr()
 */

#include <memory>
#include "bindings.hpp"
#include "pdcstring.hpp"
#include "error.hpp"

namespace dotnethost {

class Hostfxr;

class Nethost {
public:
    /**
     * 定位并加载 hostfxr 库
     * 通过 DOTNET_ROOT 环境变量定位
     * @return 已加载的 Hostfxr 实例
     * @throws HostingException 加载失败
     */
    static std::shared_ptr<Hostfxr> load_hostfxr();

private:
    Nethost() = delete;

    static PdCString get_hostfxr_path();
    static PdCString get_hostfxr_path_internal(const bindings::get_hostfxr_parameters* parameters);
    static bindings::get_hostfxr_path_fn load_nethost_function();
};

} // namespace dotnethost

#endif // DOTNETHOST_NETHOST_HPP
