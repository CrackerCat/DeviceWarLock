#ifndef WARLOCK_SYSTEMDETECTOR_H
#define WARLOCK_SYSTEMDETECTOR_H

#include "IDetector.h"
#include "../utils/allheader.h"
#include "../utils/LogUtils.h"
#include "../utils/DetectorUtils.h"
#include "../crypto/crypto.h"
#include <sys/system_properties.h>
#include <fstream>

class SystemDetector : public IDetector {
public:
    virtual ~SystemDetector() = default;
    void detect(JNIEnv* env, jobject callback) override;

private:
    static const std::string CHECK_SYSTEM;
    static const std::string CHECK_KSU;
    static const std::string CHECK_ZYGISK;
    static const std::string CHECK_PTY;
    bool checkDmVerity();
    bool checkSystemPartition();
    std::string calculateSystemHash();
    bool checkAVB();
    static void checkKsu(JNIEnv* env, jobject callback);
    static bool checkZygisk();
    std::string getSystemDetails();
    void checkPty(JNIEnv* env, jobject callback);
};

#endif