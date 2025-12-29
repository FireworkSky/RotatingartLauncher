#include "thread_affinity_manager.h"

#include "app_logger.h"

#include <iostream>
#include <fstream>
#include <sstream>
#include <string>

#define LOG_TAG "THREAD_AFFINITY_MANAGER"

int getCpuCoreNumber() {
    std::ifstream cpuinfo("/proc/cpuinfo"); // 读取 cpuinfo 文件
    int cores = 0;
    std::string line;
    while (std::getline(cpuinfo, line)) {
        if (line.substr(0, 9) == "processor") {
            cores++;
        }
    }
    cpuinfo.close();
    return cores;
}


int getMaxFreqCPUIndex(int coreNum, int &numOfBigCore) {
    int maxFreq = -1; // 频率
    int index = -1;   // CPU 位置
    numOfBigCore = 0;
    try {
        for (int i = 0; i < coreNum; i++) {
            std::string filename = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
            std::ifstream cpuFile(filename);
            if (cpuFile.good()) { // 文件成功打开
                std::string line;
                std::getline(cpuFile, line);
                std::stringstream ss(line);
                int freqBound;
                ss >> freqBound; // 从 string 流中读给 freqBound
                if (freqBound > maxFreq) {
                    numOfBigCore = 1;
                    maxFreq = freqBound;
                    index = i;
                }
                else if (freqBound == maxFreq) {
                    numOfBigCore++;
                }
                cpuFile.close();
            }
        }
    }
    catch (const std::exception& e) {
        LOGW(LOG_TAG, "Exception occurred while reading CPU frequencies: %s", e.what());
    }
    return index;
}

void setThreadAffinityToBigCores() {
    int coreNum = getCpuCoreNumber();
    if (coreNum <= 0) {
        LOGW(LOG_TAG, "Failed to get CPU core number.");
        return;
    }

    int numOfBigCore = 0;
    int bigCoreIndex = getMaxFreqCPUIndex(coreNum, numOfBigCore);
    if (bigCoreIndex == -1) {
        LOGW(LOG_TAG, "Failed to determine big core index.");
        return;
    }

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);

    for (int i = 0; i < coreNum; i++) {
        if (i >= bigCoreIndex && i < bigCoreIndex + numOfBigCore) {
            CPU_SET(i, &cpuset);
            LOGI(LOG_TAG, "Including CPU core %d in affinity set.", i);
        }
    }

    int result = sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
    if (result != 0) {
        LOGW(LOG_TAG, "Failed to set thread affinity. Error code: %d", result);
    } else {
        LOGI(LOG_TAG, "Thread affinity set to big cores successfully.");
    }
}