#ifndef WARLOCK_VIRTUALDETECTOR_H
#define WARLOCK_VIRTUALDETECTOR_H

#include "IDetector.h"
#include "../utils/LogUtils.h"
#include "../utils/MiscUtil.h"
#include <linux/seccomp.h>
#include <linux/filter.h>
#include <linux/audit.h>
#include <sys/prctl.h>
#include <errno.h>
#include "allheader.h"
#include "../utils/StringUtils.h"
#include "../utils/DetectorUtils.h"
#include <sys/system_properties.h>

class VirtualDetector : public IDetector {
public:
    void detect(JNIEnv* env, jobject callback) override;

private:
    static const std::string CHECK_BRAND_SERVICES;
    static const std::string CHECK_ARM_REGISTERS;

    static const std::string CHECK_VIRTUAL;
    static const std::string CHECK_THERMAL;
    static const std::string CHECK_PROCESS;
    static const int DetectX86Flag = 0x12345678;
    static const int MIN_THERMAL_ZONES = 2;
    static const int MAX_NORMAL_PROCESS = 2;

    void install_check_arch_seccomp();
    std::string check_arch_by_seccomp();
    void detectArch(JNIEnv* env, jobject callback);
    void detectThermal(JNIEnv* env, jobject callback);  // 新增温度检测方法
    int check_thermal_zones();  // 新增温度区检测方法
    void detectProcess(JNIEnv* env, jobject callback);  // 新增进程检测方法
    void detectBrandServices(JNIEnv* env, jobject callback);
    void detectArmRegisters(JNIEnv* env, jobject callback);  // 新增ARM寄存器检测方法
    void checkServices(const char* services[], size_t count,
                       std::string& missingServices, bool& isAbnormal);
    bool checkServiceExists(const char* serviceName);
    std::string getServiceList();
    
    // ARM寄存器读取函数
    uint64_t getMainIdRegister();
    uint64_t getMultiprocessorAffinityRegister();
    uint64_t getRevisionIdRegister();
    uint64_t getProcessorFeatureRegister0();
    uint64_t getInstructionSetAttributeRegister0();
    std::string analyzeArmHardware();

};

#endif