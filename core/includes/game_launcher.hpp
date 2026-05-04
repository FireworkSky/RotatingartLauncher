#ifndef ROTATING_ART_LAUNCHER_GAME_LAUNCHER_HPP
#define ROTATING_ART_LAUNCHER_GAME_LAUNCHER_HPP

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Launch a .NET process in a separate Android process
 *
 * @param assembly_path Path to the .NET assembly to launch
 * @param argc Number of arguments
 * @param argv Array of argument strings
 * @param title Notification title for the process
 * @param game_id Game identifier for the process
 * @return 0 on success, non-zero on failure
 */
int game_launcher_launch_new_dotnet_process(const char* assembly_path,
                                            int argc,
                                            const char* argv[],
                                            const char* title,
                                            const char* game_id);

#ifdef __cplusplus
}
#endif

#endif //ROTATING_ART_LAUNCHER_GAME_LAUNCHER_HPP
