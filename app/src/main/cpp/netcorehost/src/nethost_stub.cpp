#include "netcorehost/bindings.hpp"

#include <algorithm>
#include <cstdlib>
#include <filesystem>
#include <system_error>
#include <vector>

#ifdef _WINDOWS
#include <windows.h>
#endif

namespace fs = std::filesystem;

namespace {

using netcorehost::bindings::StatusCode;
using CharT = netcorehost::bindings::char_t;

#ifdef _WINDOWS
const fs::path::string_type kHostfxrFileName = L"hostfxr.dll";
#elif defined(__APPLE__)
const fs::path::string_type kHostfxrFileName = "libhostfxr.dylib";
#else
const fs::path::string_type kHostfxrFileName = "libhostfxr.so";
#endif

#ifdef _WINDOWS
fs::path::string_type get_env_value(const wchar_t* name) {
    const wchar_t* value = _wgetenv(name);
    return value ? fs::path::string_type(value) : fs::path::string_type();
}
#else
fs::path::string_type get_env_value(const char* name) {
    const char* value = std::getenv(name);
    return value ? fs::path::string_type(value) : fs::path::string_type();
}
#endif

fs::path::string_type to_string_type(const CharT* value) {
    return value ? fs::path::string_type(value) : fs::path::string_type();
}

template <typename StringT>
std::vector<int> parse_version_components(const StringT& name) {
    using ValueT = typename StringT::value_type;
    std::vector<int> parts;
    int current = 0;
    bool in_number = false;

    for (ValueT ch : name) {
        if (ch >= static_cast<ValueT>('0') && ch <= static_cast<ValueT>('9')) {
            current = current * 10 + static_cast<int>(ch - static_cast<ValueT>('0'));
            in_number = true;
        } else if (in_number) {
            parts.push_back(current);
            current = 0;
            in_number = false;
        }
    }

    if (in_number) {
        parts.push_back(current);
    }

    return parts;
}

bool is_version_greater(const fs::path::string_type& a, const fs::path::string_type& b) {
    const auto va = parse_version_components(a);
    const auto vb = parse_version_components(b);
    const size_t count = std::max(va.size(), vb.size());

    for (size_t i = 0; i < count; ++i) {
        const int ai = i < va.size() ? va[i] : 0;
        const int bi = i < vb.size() ? vb[i] : 0;
        if (ai != bi) {
            return ai > bi;
        }
    }

    if (a.size() != b.size()) {
        return a.size() > b.size();
    }

    return a > b;
}

bool pick_latest_subdirectory(const fs::path& base_dir, fs::path& out_dir) {
    std::error_code ec;
    if (!fs::exists(base_dir, ec) || !fs::is_directory(base_dir, ec)) {
        return false;
    }

    bool has_best = false;
    fs::path best_path;
    fs::path::string_type best_name;

    for (const auto& entry : fs::directory_iterator(base_dir, fs::directory_options::skip_permission_denied, ec)) {
        if (ec) {
            continue;
        }
        if (!entry.is_directory(ec) || ec) {
            continue;
        }

        const auto name = entry.path().filename().native();
        if (!has_best || is_version_greater(name, best_name)) {
            best_path = entry.path();
            best_name = name;
            has_best = true;
        }
    }

    if (!has_best) {
        return false;
    }

    out_dir = best_path;
    return true;
}

bool try_candidate(const fs::path& candidate, fs::path& result) {
    std::error_code ec;
    if (fs::exists(candidate, ec) && fs::is_regular_file(candidate, ec)) {
        result = candidate;
        return true;
    }
    return false;
}

bool locate_hostfxr(const fs::path& root, fs::path& out_path) {
    if (try_candidate(root / kHostfxrFileName, out_path)) {
        return true;
    }

    fs::path fxr_dir;
    if (pick_latest_subdirectory(root / "host" / "fxr", fxr_dir)) {
        if (try_candidate(fxr_dir / kHostfxrFileName, out_path)) {
            return true;
        }
    }

    fs::path shared_dir;
    if (pick_latest_subdirectory(root / "shared" / "Microsoft.NETCore.App", shared_dir)) {
        if (try_candidate(shared_dir / kHostfxrFileName, out_path)) {
            return true;
        }
    }

    // 如果 root 已经是 host/fxr/<version> 目录，尝试直接查找
    auto parent = root.parent_path();
    if (parent.filename() == "fxr") {
        if (try_candidate(root / kHostfxrFileName, out_path)) {
            return true;
        }
    }

    return false;
}

} // namespace

extern "C" NETHOST_API int32_t NETHOST_CALLTYPE get_hostfxr_path(
    CharT* buffer,
    size_t* buffer_size,
    const netcorehost::bindings::get_hostfxr_parameters* parameters) {

    using netcorehost::bindings::StatusCode;

    if (buffer_size == nullptr) {
        return static_cast<int32_t>(StatusCode::InvalidArgFailure);
    }

    const fs::path::string_type dotnet_root_string = [&]() -> fs::path::string_type {
        if (parameters != nullptr && parameters->dotnet_root != nullptr && parameters->dotnet_root[0] != 0) {
            return to_string_type(parameters->dotnet_root);
        }

#ifdef _WINDOWS
        auto env_value = get_env_value(L"DOTNET_ROOT");
#else
        auto env_value = get_env_value("DOTNET_ROOT");
#endif

        if (!env_value.empty()) {
            return env_value;
        }

        if (parameters != nullptr && parameters->assembly_path != nullptr && parameters->assembly_path[0] != 0) {
            fs::path assembly_path(to_string_type(parameters->assembly_path));
            auto parent = assembly_path.parent_path();
            if (!parent.empty()) {
                return parent.native();
            }
        }

        return fs::path::string_type();
    }();

    if (dotnet_root_string.empty()) {
        *buffer_size = 0;
        return static_cast<int32_t>(StatusCode::CoreHostLibMissingFailure);
    }

    fs::path dotnet_root(dotnet_root_string);
    fs::path hostfxr_path;
    if (!locate_hostfxr(dotnet_root, hostfxr_path)) {
        *buffer_size = 0;
        return static_cast<int32_t>(StatusCode::CoreHostLibMissingFailure);
    }

    const auto native_path = hostfxr_path.native();
    const size_t required_size = native_path.size() + 1;

    if (buffer == nullptr || *buffer_size < required_size) {
        *buffer_size = required_size;
        return static_cast<int32_t>(StatusCode::HostApiBufferTooSmall);
    }

    std::copy(native_path.begin(), native_path.end(), buffer);
    buffer[native_path.size()] = static_cast<CharT>(0);
    *buffer_size = required_size;
    return static_cast<int32_t>(StatusCode::Success);
}

