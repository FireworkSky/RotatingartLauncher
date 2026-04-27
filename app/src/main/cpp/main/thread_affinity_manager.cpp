#include "thread_affinity_manager.h"

#include "logging/app_log.h"

#include <cerrno>
#include <cstring>
#include <fstream>
#include <jni.h>
#include <sched.h>
#include <string>
#include <unistd.h>
#include <vector>

#define LOG_TAG "THREAD_AFFINITY_MANAGER"

namespace {

constexpr int kErrCpuCountUnavailable = -1001;
constexpr int kErrBigCoreUnavailable = -1002;

int getCpuCoreNumberFromProcCpuInfo() {
    std::ifstream cpuinfo("/proc/cpuinfo");
    int cores = 0;
    std::string line;
    while (std::getline(cpuinfo, line)) {
        if (line.compare(0, 9, "processor") == 0) {
            cores++;
        }
    }
    return cores;
}

int getCpuCoreNumber() {
    long coreCount = sysconf(_SC_NPROCESSORS_CONF);
    if (coreCount <= 0) {
        coreCount = sysconf(_SC_NPROCESSORS_ONLN);
    }
    if (coreCount > 0) {
        return static_cast<int>(coreCount);
    }

    return getCpuCoreNumberFromProcCpuInfo();
}

bool readCpuMaxFrequency(int cpuIndex, long &maxFrequency) {
    const std::string filename = "/sys/devices/system/cpu/cpu" +
        std::to_string(cpuIndex) +
        "/cpufreq/cpuinfo_max_freq";
    std::ifstream cpuFile(filename);
    if (!cpuFile.is_open()) {
        return false;
    }

    cpuFile >> maxFrequency;
    return !cpuFile.fail();
}

std::vector<int> getBigCoreCpuIds(int coreNum) {
    std::vector<int> bigCoreIds;
    long maxFrequency = -1;

    for (int i = 0; i < coreNum && i < CPU_SETSIZE; ++i) {
        long cpuFrequency = -1;
        if (!readCpuMaxFrequency(i, cpuFrequency)) {
            LOGD(LOG_TAG, "CPU core %d max frequency is unavailable.", i);
            continue;
        }

        if (cpuFrequency > maxFrequency) {
            maxFrequency = cpuFrequency;
            bigCoreIds.clear();
            bigCoreIds.push_back(i);
        } else if (cpuFrequency == maxFrequency) {
            bigCoreIds.push_back(i);
        }
    }

    if (!bigCoreIds.empty()) {
        LOGI(
            LOG_TAG,
            "Detected %zu big core(s) with max frequency %ld kHz.",
            bigCoreIds.size(),
            maxFrequency
        );
    }

    return bigCoreIds;
}

std::string formatCpuSet(const cpu_set_t &cpuset) {
    std::string result;
    for (int i = 0; i < CPU_SETSIZE; ++i) {
        if (!CPU_ISSET(i, &cpuset)) {
            continue;
        }

        if (!result.empty()) {
            result += ",";
        }
        result += std::to_string(i);
    }

    return result.empty() ? "<empty>" : result;
}

} // namespace

int setThreadAffinityToBigCores() {
    int coreNum = getCpuCoreNumber();
    if (coreNum <= 0) {
        LOGW(LOG_TAG, "Failed to get CPU core number.");
        return kErrCpuCountUnavailable;
    }

    if (coreNum > CPU_SETSIZE) {
        LOGW(
            LOG_TAG,
            "Detected %d CPU cores, but cpu_set_t supports only %d. Extra cores will be ignored.",
            coreNum,
            CPU_SETSIZE
        );
    }

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);

    const std::vector<int> bigCoreIds = getBigCoreCpuIds(coreNum);
    if (bigCoreIds.empty()) {
        LOGW(LOG_TAG, "Failed to determine big core CPU ids.");
        return kErrBigCoreUnavailable;
    }

    for (int cpuId : bigCoreIds) {
        CPU_SET(cpuId, &cpuset);
        LOGI(LOG_TAG, "Including CPU core %d in affinity set.", cpuId);
    }

    int result = sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
    if (result != 0) {
        const int savedErrno = errno;
        LOGW(
            LOG_TAG,
            "Failed to set thread affinity. result=%d errno=%d (%s)",
            result,
            savedErrno,
            strerror(savedErrno)
        );
        LOGW(LOG_TAG, "This error is non-fatal; failing to set thread affinity will not stop launch.");
        return result;
    }

    cpu_set_t actualCpuset;
    CPU_ZERO(&actualCpuset);
    if (sched_getaffinity(0, sizeof(cpu_set_t), &actualCpuset) == 0) {
        const std::string requestedMask = formatCpuSet(cpuset);
        const std::string actualMask = formatCpuSet(actualCpuset);
        LOGI(
            LOG_TAG,
            "Thread affinity set to big cores successfully. requested=[%s], actual=[%s]",
            requestedMask.c_str(),
            actualMask.c_str()
        );
    } else {
        const int savedErrno = errno;
        LOGW(
            LOG_TAG,
            "Thread affinity set, but failed to read actual affinity. errno=%d (%s)",
            savedErrno,
            strerror(savedErrno)
        );
    }

    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_platform_runtime_ThreadAffinityManager_nativeSetThreadAffinityToBigCores(JNIEnv *env, jobject thiz) {
    return setThreadAffinityToBigCores();
}
