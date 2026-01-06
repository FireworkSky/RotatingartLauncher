#ifndef ROTATING_ART_LAUNCHER_SHARED_ENVVARS_HPP
#define ROTATING_ART_LAUNCHER_SHARED_ENVVARS_HPP

#include <string>

namespace ral::shared_envvars {

    static std::string get_package_name();
    static std::string get_external_storage_directory();
    static bool is_set_thread_affinity_to_big_core();

} // ral

#endif //ROTATING_ART_LAUNCHER_SHARED_ENVVARS_HPP
