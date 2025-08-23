#include "../../inc/detector/NativeDetector.h"

#include "../../inc/utils/LogUtils.h"

NativeDetector* NativeDetector::instance = nullptr;

NativeDetector::NativeDetector() : javaVM(nullptr), globalCallback(nullptr) {
    initDetectors();
}

NativeDetector::~NativeDetector() {
    cleanup();
}

void NativeDetector::initDetectors() {
    detectors.push_back(std::make_unique<VirtualDetector>());
    detectors.push_back(std::make_unique<FridaDetector>());
    detectors.push_back(std::make_unique<MiscDetector>());
    detectors.push_back(std::make_unique<SystemDetector>());
    detectors.push_back(std::make_unique<HookDetector>());

    // 可以添加其他检测器
}

NativeDetector* NativeDetector::getInstance() {
    if (instance == nullptr) {
        instance = new NativeDetector();
    }
    return instance;
}

void NativeDetector::startDetect(JNIEnv* env, jobject callback) {
    if (javaVM != nullptr) {
        return;
    }

    env->GetJavaVM(&javaVM);
    globalCallback = env->NewGlobalRef(callback);

    detect();
}

JNIEnv* NativeDetector::getEnv() {
    if (!javaVM) {
        return nullptr;
    }

    JNIEnv* env = nullptr;
    int status = javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (status == JNI_EDETACHED) {
        status = javaVM->AttachCurrentThread(&env, nullptr);
        if (status < 0) {
            return nullptr;
        }
    }

    return env;
}

void NativeDetector::detect() {
    JNIEnv* env = getEnv();
    if (!env) {
        LOGE("Failed to get JNIEnv");
        return;
    }

    try {
        for (const auto& detector : detectors) {
            detector->detect(env, globalCallback);
        }
    } catch (const std::exception& e) {
        LOGE("Error during detection: %s", e.what());
    }
}

void NativeDetector::cleanup() {
    if (javaVM && globalCallback) {
        JNIEnv* env = getEnv();
        if (env) {
            env->DeleteGlobalRef(globalCallback);
        }
    }

    javaVM = nullptr;
    globalCallback = nullptr;
    detectors.clear();
}