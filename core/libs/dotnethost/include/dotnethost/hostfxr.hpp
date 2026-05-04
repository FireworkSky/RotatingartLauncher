#ifndef DOTNETHOST_HOSTFXR_HPP
#define DOTNETHOST_HOSTFXR_HPP

/**
 * Hostfxr 库包装器
 * 仅保留命令行启动路径（initialize_for_dotnet_command_line + run_app）
 */

#include <string>
#include <memory>
#include "bindings.hpp"
#include "pdcstring.hpp"
#include "error.hpp"

namespace dotnethost {

class HostfxrContextForCommandLine;

class Hostfxr : public std::enable_shared_from_this<Hostfxr> {
public:
    ~Hostfxr();

    /**
     * 从指定路径加载 hostfxr 动态库
     * @param path hostfxr 库文件路径
     * @return Hostfxr 共享实例
     * @throws HostingException 路径无效或加载失败
     */
    static std::shared_ptr<Hostfxr> load_from_path(const PdCString& path);

    /**
     * 初始化 .NET 运行时（带参数和 dotnet_root）
     * @param assembly_path 要运行的程序集路径
     * @param argc 额外命令行参数数量
     * @param argv 额外命令行参数
     * @param dotnet_root .NET 运行时根目录
     * @return 命令行上下文
     * @throws HostingException 初始化失败
     */
    std::unique_ptr<HostfxrContextForCommandLine> initialize_for_command_line(
        const PdCString& assembly_path,
        int argc,
        const char* const* argv,
        const PdCString& dotnet_root
    );

    // 内部访问
    bindings::hostfxr_run_app_fn get_run_app_fn() const { return run_app_fn_; }
    bindings::hostfxr_close_fn get_close_fn() const { return close_fn_; }

    /**
     * 注册错误输出回调（线程局部）
     * @param writer 回调函数，nullptr 取消注册
     * @return 之前注册的回调
     */
    bindings::hostfxr_error_writer_fn set_error_writer(bindings::hostfxr_error_writer_fn writer);

private:
    Hostfxr(void* library_handle, const PdCString& hostfxr_path);

    void* library_handle_;
    PdCString hostfxr_path_;

    // 函数指针 - 仅保留实际使用的
    bindings::hostfxr_initialize_for_dotnet_command_line_fn initialize_for_dotnet_command_line_fn_;
    bindings::hostfxr_run_app_fn run_app_fn_;
    bindings::hostfxr_close_fn close_fn_;
    bindings::hostfxr_set_error_writer_fn set_error_writer_fn_;

    void load_functions();
    static bool validate_library_path(const std::string& path);
};

} // namespace dotnethost

#endif // DOTNETHOST_HOSTFXR_HPP
