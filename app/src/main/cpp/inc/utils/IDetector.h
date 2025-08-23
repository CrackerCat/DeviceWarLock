#ifndef WARLOCK_IDETECTOR_H
#define WARLOCK_IDETECTOR_H

#include <jni.h>

class IDetector {
public:
    virtual ~IDetector() = default;
    virtual void detect(JNIEnv* env, jobject callback) = 0;
};

#endif // WARLOCK_IDETECTOR_H//
// Created by 17267 on 2024-12-24.
//

#
