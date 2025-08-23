

#include "../../inc/detector/HookDetector.h"
#include "../../inc/utils/SyscallUtils.h"
#include "../../inc/utils/FileUtils.h"
#include "../../inc/utils/MiscUtil.h"
#include <sstream>
#include <string>
#include <vector>
#include <set>
#include <zlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/mman.h>


const std::string HookDetector::CHECK_CRC_SO = "checkCrcSo_native";
const std::string HookDetector::checkMemoryMaps_native = "checkMemoryMaps_native";

void HookDetector::detect(JNIEnv* env, jobject callback) {
    checkRWXSegment(env, callback);
}

void HookDetector::checkRWXSegment(JNIEnv* env, jobject callback){
    LOGI("Starting memory maps RWX segment detection");

    std::vector<std::string> rwx_segments;

    // 读取 /proc/self/maps 文件
    std::string maps_content = utils::FileUtils::readFileAsString("/proc/self/maps");
    if (maps_content.empty()) {
        LOGE("Failed to read /proc/self/maps");
        return;
    }

    // 解析每一行
    std::istringstream iss(maps_content);
    std::string line;

    while (std::getline(iss, line)) {
        if (line.empty()) continue;

        // 解析内存映射行格式: address perms offset dev inode pathname
        // 例如: 7f8b8c000000-7f8b8c021000 rwxp 00000000 00:00 0
        std::istringstream line_stream(line);
        std::string address_range, permissions, offset, dev, inode, pathname;

        line_stream >> address_range >> permissions >> offset >> dev >> inode;

        // 获取路径名（可能包含空格）
        if (line_stream.peek() != EOF) {
            line_stream.ignore(); // 跳过空格
            std::getline(line_stream, pathname);
        }

        // 检查是否为可读可写可执行段 (rwx)
        if (permissions.length() >= 3 &&
            permissions[0] == 'r' &&
            permissions[1] == 'w' &&
            permissions[2] == 'x') {

            std::string segment_info = "RWX segment found: " + address_range + " " + permissions;
            if (!pathname.empty()) {
                segment_info += " [" + pathname + "]";
            }

            rwx_segments.push_back(segment_info);
            LOGW("Detected RWX memory segment: %s", segment_info.c_str());
        }
    }

    // 如果发现可疑的RWX段，进行上报
    if (!rwx_segments.empty()) {
        std::string warning_message = "Detected " + std::to_string(rwx_segments.size()) + " RWX memory segments";

        // 使用工具类进行上报
        DetectorUtils::reportWarning(env, callback, checkMemoryMaps_native, DetectorUtils::LEVEL_HIGH, warning_message);

        // 输出详细信息到日志
        for (const std::string& segment : rwx_segments) {
            LOGI("RWX Segment: %s", segment.c_str());
        }
    } else {
        LOGI("No RWX memory segments detected");
    }
}

