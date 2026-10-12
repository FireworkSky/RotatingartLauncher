#ifndef ROTATING_ART_LAUNCHER_MONO_LAUNCHER_HPP
#define ROTATING_ART_LAUNCHER_MONO_LAUNCHER_HPP

#include <string>
#include <vector>

namespace RALauncher::Mono {

    /**
     * Mono 运行时启动器
     *
     * 通过运行时 dlsym 解析 Mono 嵌入 API（不依赖 mono 开发头文件），
     * 用 mono_jit_init_version + mono_domain_assembly_open + mono_jit_exec
     * 在宿主进程中启动一个托管程序集。
     */
    class MonoLauncher {
    public:
        /**
         * 启动托管程序集
         *
         * @param assembly_path 程序集（.exe/.dll）路径
         * @param args          传给程序集的参数（不含程序集自身路径）
         * @param mono_root     Mono 运行时根目录（runtimes/mono/<version>）
         * @param bcl_dir       BCL 程序集目录（含 mscorlib.dll），为空时回退到 <mono_root>/bcl
         * @return 程序集退出码；负数表示启动失败
         */
        static int launch(const std::string& assembly_path,
                          std::vector<std::string> args,
                          const std::string& mono_root,
                          const std::string& bcl_dir);

        /** 上一次启动的错误信息（空字符串表示无错误） */
        static std::string last_error();

    private:
        MonoLauncher() = delete;
    };

} // namespace RALauncher::Mono

#endif //ROTATING_ART_LAUNCHER_MONO_LAUNCHER_HPP
