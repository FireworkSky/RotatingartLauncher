#include "dotnethost/pdcstring.hpp"
#include <cstring>

#ifdef _WINDOWS
    #include <windows.h>
#else
    #include <codecvt>
    #include <locale>
#endif

namespace dotnethost {

PdCString::PdCString() : data_(nullptr), length_(0) {}
PdCString::~PdCString() = default;

#ifdef _WINDOWS

PdCString::PdCString(const std::wstring& str) : length_(str.length()) {
    data_ = std::make_unique<bindings::char_t[]>(length_ + 1);
    std::wcscpy(data_.get(), str.c_str());
}

PdCString::PdCString(const wchar_t* str) {
    if (str) {
        length_ = std::wcslen(str);
        data_ = std::make_unique<bindings::char_t[]>(length_ + 1);
        std::wcscpy(data_.get(), str);
    } else {
        length_ = 0;
        data_ = nullptr;
    }
}

PdCString PdCString::from_str(const std::string& str) {
    int len = MultiByteToWideChar(CP_UTF8, 0, str.c_str(), -1, nullptr, 0);
    if (len == 0) return PdCString();
    std::wstring wstr(len - 1, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, str.c_str(), -1, &wstr[0], len);
    return PdCString(wstr);
}

std::string PdCString::to_string() const {
    if (!data_ || length_ == 0) return {};
    int len = WideCharToMultiByte(CP_UTF8, 0, data_.get(), -1, nullptr, 0, nullptr, nullptr);
    if (len == 0) return {};
    std::string result(len - 1, '\0');
    WideCharToMultiByte(CP_UTF8, 0, data_.get(), -1, &result[0], len, nullptr, nullptr);
    return result;
}

#else // Unix/Linux/Android

PdCString::PdCString(const std::string& str) : length_(str.length()) {
    data_ = std::make_unique<bindings::char_t[]>(length_ + 1);
    std::strcpy(data_.get(), str.c_str());
}

PdCString::PdCString(const char* str) {
    if (str) {
        length_ = std::strlen(str);
        data_ = std::make_unique<bindings::char_t[]>(length_ + 1);
        std::strcpy(data_.get(), str);
    } else {
        length_ = 0;
        data_ = nullptr;
    }
}

PdCString PdCString::from_str(const std::string& str) {
    return PdCString(str);
}

std::string PdCString::to_string() const {
    if (!data_) return {};
    return std::string(data_.get());
}

#endif

PdCString::PdCString(const PdCString& other) : length_(other.length_) {
    if (other.data_) {
        data_ = std::make_unique<bindings::char_t[]>(length_ + 1);
#ifdef _WINDOWS
        std::wcscpy(data_.get(), other.data_.get());
#else
        std::strcpy(data_.get(), other.data_.get());
#endif
    }
}

PdCString::PdCString(PdCString&& other) noexcept
    : data_(std::move(other.data_)), length_(other.length_) {
    other.length_ = 0;
}

PdCString& PdCString::operator=(const PdCString& other) {
    if (this != &other) {
        length_ = other.length_;
        if (other.data_) {
            data_ = std::make_unique<bindings::char_t[]>(length_ + 1);
#ifdef _WINDOWS
            std::wcscpy(data_.get(), other.data_.get());
#else
            std::strcpy(data_.get(), other.data_.get());
#endif
        } else {
            data_.reset();
        }
    }
    return *this;
}

PdCString& PdCString::operator=(PdCString&& other) noexcept {
    if (this != &other) {
        data_ = std::move(other.data_);
        length_ = other.length_;
        other.length_ = 0;
    }
    return *this;
}

const bindings::char_t* PdCString::c_str() const {
    return data_ ? data_.get() : PDCSTR("");
}

bool PdCString::empty() const { return length_ == 0; }
size_t PdCString::length() const { return length_; }

} // namespace dotnethost
