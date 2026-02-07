#ifndef DOTNETHOST_CONTEXT_HPP
#define DOTNETHOST_CONTEXT_HPP

/**
 * Hostfxr 运行上下文
 * 仅保留命令行运行上下文（HostfxrContextForCommandLine）
 */

#include <memory>
#include "bindings.hpp"
#include "error.hpp"

namespace dotnethost {

class Hostfxr;

/**
 * 命令行运行上下文
 * 通过 Hostfxr::initialize_for_command_line() 创建
 */
class HostfxrContextForCommandLine {
public:
    HostfxrContextForCommandLine(
        bindings::hostfxr_handle handle,
        std::shared_ptr<Hostfxr> hostfxr
    );
    ~HostfxrContextForCommandLine();

    // 禁止拷贝，允许移动
    HostfxrContextForCommandLine(const HostfxrContextForCommandLine&) = delete;
    HostfxrContextForCommandLine& operator=(const HostfxrContextForCommandLine&) = delete;
    HostfxrContextForCommandLine(HostfxrContextForCommandLine&& other) noexcept;
    HostfxrContextForCommandLine& operator=(HostfxrContextForCommandLine&& other) noexcept;

    /**
     * 运行应用程序（阻塞直到应用退出）
     * @return 应用退出码或托管错误码
     */
    AppOrHostingResult run_app();

    /**
     * 显式关闭上下文（析构函数也会自动关闭）
     */
    void close();

    bindings::hostfxr_handle handle() const { return handle_; }

private:
    bindings::hostfxr_handle handle_;
    std::shared_ptr<Hostfxr> hostfxr_;
    bool closed_;
};

} // namespace dotnethost

#endif // DOTNETHOST_CONTEXT_HPP
