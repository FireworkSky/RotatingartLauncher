#ifndef ROTATING_ART_LAUNCHER_DOTNET_LAUNCHER_HPP
#define ROTATING_ART_LAUNCHER_DOTNET_LAUNCHER_HPP

#include <string>
#include <vector>

namespace RALauncher::Dotnet {

    class DotnetLauncher {

    public:
        static std::string hostfxr_last_error_msg;

        static int hostfxr_launch(const std::string& assembly_path, std::vector<std::string> args, const std::string& dotnet_root = "");

    private:
        // Prevent instantiation
        DotnetLauncher() = delete;

    }; // dotnet_launcher

} // RALauncher::Dotnet

#endif //ROTATING_ART_LAUNCHER_DOTNET_LAUNCHER_HPP
