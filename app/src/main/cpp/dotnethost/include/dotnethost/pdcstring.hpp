#ifndef DOTNETHOST_PDCSTRING_HPP
#define DOTNETHOST_PDCSTRING_HPP

/**
 * 平台相关字符串类型
 * Windows: wchar_t (UTF-16)  |  其他平台: char (UTF-8)
 */

#include <string>
#include <memory>
#include "bindings.hpp"

namespace dotnethost {

class PdCString {
public:
    PdCString();
    ~PdCString();

    static PdCString from_str(const std::string& str);

#ifdef _WINDOWS
    explicit PdCString(const std::wstring& str);
    explicit PdCString(const wchar_t* str);
#else
    explicit PdCString(const std::string& str);
    explicit PdCString(const char* str);
#endif

    PdCString(const PdCString& other);
    PdCString(PdCString&& other) noexcept;
    PdCString& operator=(const PdCString& other);
    PdCString& operator=(PdCString&& other) noexcept;

    const bindings::char_t* c_str() const;
    std::string to_string() const;

    bool empty() const;
    size_t length() const;

private:
    std::unique_ptr<bindings::char_t[]> data_;
    size_t length_;
};

#ifdef _WINDOWS
    #define PDCSTR(str) L##str
#else
    #define PDCSTR(str) str
#endif

} // namespace dotnethost

#endif // DOTNETHOST_PDCSTRING_HPP
