#pragma once

#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <ctime>
#include <fcntl.h>
#include <mutex>
#include <poll.h>
#include <string>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>

namespace RALauncher::Dotnet {

// Console.Write uses write(2) on Unix; libc formatting hooks cannot capture it.
// Own both descriptors only for the host lifetime. No managed patch is required.
class DotnetOutputCapture {
public:
    explicit DotnetOutputCapture(const char* path, size_t max_bytes = 2 * 1024 * 1024)
        : path_(path ? path : ""), max_bytes_(max_bytes), ownership_(owner_, std::defer_lock) {
        if (path_.empty() || !ownership_.try_lock()) return;
        open_log(true);
        for (auto& stream : streams_) {
            stream.saved = fcntl(stream.target, F_DUPFD_CLOEXEC, 3);
            int descriptors[2];
            if (stream.saved < 0 || pipe2(descriptors, O_CLOEXEC) != 0) {
                __android_log_write(ANDROID_LOG_WARN, "DOTNET/capture", "Cannot create console pipes; leaving console descriptors unchanged");
                cleanup();
                return;
            }
            stream.read_fd = descriptors[0];
            stream.write_fd = descriptors[1];
            fcntl(stream.read_fd, F_SETFL, O_NONBLOCK);
        }
        // Start the drain before redirecting so a failed thread creation leaves stdout intact.
        try {
            reader_ = std::thread([this] { drain(); });
        } catch (...) {
            __android_log_write(ANDROID_LOG_WARN, "DOTNET/capture", "Cannot start console reader; leaving console descriptors unchanged");
            cleanup();
            return;
        }
        fflush(stdout);
        fflush(stderr);
        for (auto& stream : streams_) {
            if (dup2(stream.write_fd, stream.target) < 0) {
                __android_log_write(ANDROID_LOG_WARN, "DOTNET/capture", "Cannot redirect console descriptors; restoring them");
                stop();
                return;
            }
            stream.redirected = true;
            close(stream.write_fd);
            stream.write_fd = -1;
        }
        __android_log_write(ANDROID_LOG_INFO, "DOTNET/capture", "stdout/stderr capture started");
    }

    ~DotnetOutputCapture() { stop(); }
    DotnetOutputCapture(const DotnetOutputCapture&) = delete;
    DotnetOutputCapture& operator=(const DotnetOutputCapture&) = delete;

private:
    struct Stream {
        int target;
        const char* tag;
        int priority;
        int saved = -1;
        int read_fd = -1;
        int write_fd = -1;
        bool redirected = false;
        std::string pending;
    };

    static size_t utf8_boundary(const std::string& text, size_t limit) {
        size_t length = std::min(text.size(), limit);
        if (length < text.size()) {
            while (length > 0 && (static_cast<unsigned char>(text[length]) & 0xc0) == 0x80) --length;
        } else if (length > 0) {
            // A write may end halfway through a code point. Keep it for the next read.
            size_t start = length - 1;
            while (start > 0 && (static_cast<unsigned char>(text[start]) & 0xc0) == 0x80) --start;
            const auto lead = static_cast<unsigned char>(text[start]);
            const size_t expected = lead < 0x80 ? 1 : lead < 0xe0 ? 2 : lead < 0xf0 ? 3 : 4;
            if (length - start < expected) length = start;
        }
        return length;
    }

    void open_log(bool rotate) {
        if (file_fd_ >= 0) close(file_fd_);
        if (rotate) rename(path_.c_str(), (path_ + ".previous").c_str());
        file_fd_ = open(path_.c_str(), O_CREAT | O_WRONLY | O_APPEND | O_CLOEXEC, 0600);
        struct stat info{};
        file_bytes_ = file_fd_ >= 0 && fstat(file_fd_, &info) == 0 ? info.st_size : 0;
        if (file_fd_ < 0) {
            __android_log_write(ANDROID_LOG_WARN, "DOTNET/capture", "Console file unavailable; logcat capture remains enabled");
        }
    }

    void emit(Stream& stream, const std::string& line) {
        __android_log_write(stream.priority, stream.tag, line.c_str());
        if (file_fd_ < 0) return;
        if (file_bytes_ >= max_bytes_) open_log(true);
        if (file_fd_ < 0) return;
        timespec now{};
        clock_gettime(CLOCK_REALTIME, &now);
        std::tm local{};
        localtime_r(&now.tv_sec, &local);
        char date[32];
        std::strftime(date, sizeof(date), "%Y-%m-%d %H:%M:%S", &local);
        char prefix[128];
        std::snprintf(prefix, sizeof(prefix), "[%s.%03ld] [%c] [%s] [%d] ",
                      date, now.tv_nsec / 1000000, stream.priority == ANDROID_LOG_ERROR ? 'E' : 'I',
                      stream.tag, getpid());
        const std::string record = std::string(prefix) + line + "\n";
        size_t offset = 0;
        while (offset < record.size()) {
            const ssize_t count = write(file_fd_, record.data() + offset, record.size() - offset);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) {
                __android_log_write(ANDROID_LOG_WARN, "DOTNET/capture", "Console file write failed; continuing in logcat");
                close(file_fd_);
                file_fd_ = -1;
                break;
            }
            offset += static_cast<size_t>(count);
        }
        file_bytes_ += offset;
    }

    void flush_pending(Stream& stream, bool partial) {
        while (!stream.pending.empty()) {
            const size_t newline = stream.pending.find('\n');
            if (newline == std::string::npos && stream.pending.size() < 3000 && !partial) break;
            size_t length = utf8_boundary(stream.pending, std::min<size_t>(newline, 3000));
            if (length == 0 && newline != 0) break;
            emit(stream, stream.pending.substr(0, length));
            stream.pending.erase(0, length + (length == newline ? 1 : 0));
        }
    }

    void drain() {
        // Bound shutdown even when a library holds a duplicate of a pipe's write end.
        int shutdown_polls = 0;
        while (shutdown_polls < 20) {
            pollfd descriptors[2] = {{streams_[0].read_fd, POLLIN, 0}, {streams_[1].read_fd, POLLIN, 0}};
            const int ready = poll(descriptors, 2, 100);
            if (ready < 0 && errno == EINTR) continue;
            if (stopping_.load()) ++shutdown_polls;
            bool read_any = false;
            for (size_t i = 0; i < 2; ++i) {
                auto& stream = streams_[i];
                if (descriptors[i].revents & (POLLIN | POLLHUP)) {
                    char buffer[4096];
                    const ssize_t count = read(stream.read_fd, buffer, sizeof(buffer));
                    if (count > 0) {
                        read_any = true;
                        stream.pending.append(buffer, static_cast<size_t>(count));
                    }
                }
                flush_pending(stream, !read_any);
            }
            if (stopping_.load() && !read_any) break;
            // Restored descriptors close the pipe, but a long-lived host may still have no output.
            if (!read_any && ready > 0) std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }
        for (auto& stream : streams_) flush_pending(stream, true);
    }

    void stop() {
        if (reader_.joinable()) {
            fflush(stdout);
            fflush(stderr);
        }
        for (auto& stream : streams_) {
            if (stream.redirected) {
                dup2(stream.saved, stream.target);
                stream.redirected = false;
            }
        }
        stopping_.store(true);
        if (reader_.joinable()) reader_.join();
        cleanup();
    }

    void cleanup() {
        for (auto& stream : streams_) {
            for (int* fd : {&stream.saved, &stream.read_fd, &stream.write_fd}) {
                if (*fd >= 0) { close(*fd); *fd = -1; }
            }
        }
        if (file_fd_ >= 0) { close(file_fd_); file_fd_ = -1; }
    }

    inline static std::mutex owner_;
    std::string path_;
    size_t max_bytes_;
    std::unique_lock<std::mutex> ownership_;
    Stream streams_[2] = {{STDOUT_FILENO, "DOTNET/stdout", ANDROID_LOG_INFO},
                          {STDERR_FILENO, "DOTNET/stderr", ANDROID_LOG_ERROR}};
    int file_fd_ = -1;
    size_t file_bytes_ = 0;
    std::atomic_bool stopping_{false};
    std::thread reader_;
};

} // namespace RALauncher::Dotnet
