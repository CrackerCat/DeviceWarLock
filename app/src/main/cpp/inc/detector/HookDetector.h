//
// Created by admin on 2025/8/4.
//

#ifndef WARLOCK_HOOKDETECTOR_H
#define WARLOCK_HOOKDETECTOR_H

#include "IDetector.h"
#include "../utils/allheader.h"
#include "../utils/LogUtils.h"
#include "../utils/DetectorUtils.h"


class HookDetector : public IDetector {
public:
    virtual ~HookDetector() = default;
    void detect(JNIEnv* env, jobject callback) override;

private:
    static const std::string CHECK_CRC_SO;
    static const std::string checkMemoryMaps_native;

    void checkCrcSo(JNIEnv* env, jobject callback);
    static void checkRWXSegment(JNIEnv* env, jobject callback);
    static void checkLibcCRC(JNIEnv* env, jobject callback);
    static uint32_t calculateFileCRC(const std::string& file_path);
    static uint32_t calculateMemoryCRC(void* addr, size_t size);
};

#endif //WARLOCK_HOOKDETECTOR_H
