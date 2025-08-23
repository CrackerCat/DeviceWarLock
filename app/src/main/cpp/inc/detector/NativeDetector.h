#ifndef WARLOCK_NATIVEDETECTOR_H
#define WARLOCK_NATIVEDETECTOR_H

#include <jni.h>
#include <vector>
#include <memory>
#include "IDetector.h"
#include "FridaDetector.h"
#include "VirtualDetector.h"
#include "MiscDetector.h"
#include "SystemDetector.h"
#include "HookDetector.h"
class NativeDetector {
public:
    static NativeDetector* getInstance();
    void startDetect(JNIEnv* env, jobject callback);
    void cleanup();

private:
    NativeDetector();
    ~NativeDetector();

    void detect();
    void initDetectors();
    JNIEnv* getEnv();

    static NativeDetector* instance;
    std::vector<std::unique_ptr<IDetector>> detectors;

    JavaVM* javaVM;
    jobject globalCallback;
};

#endif