cmake_minimum_required(VERSION 3.22.1)
project("warlockCore")
#添加内联支持
enable_language(C ASM)

# RapidJSON 相关配置
set(RAPIDJSON_BUILD_TESTS OFF CACHE BOOL "Build rapidjson tests" FORCE)
set(RAPIDJSON_BUILD_EXAMPLES OFF CACHE BOOL "Build rapidjson examples" FORCE)
set(RAPIDJSON_BUILD_DOC OFF CACHE BOOL "Build rapidjson documentation" FORCE)

include(FetchContent)
FetchContent_Declare(
        rapidjson
        GIT_REPOSITORY https://github.com/Tencent/rapidjson.git
        GIT_TAG master
)
FetchContent_MakeAvailable(rapidjson)

# 只支持ARM架构
if (${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    set(syscall src/netlink/syscall64.s)
elseif (${CMAKE_ANDROID_ARCH_ABI} STREQUAL "armeabi-v7a")
    set(syscall src/netlink/syscall32.s)
    elseif (${CMAKE_ANDROID_ARCH_ABI} STREQUAL "x86")
    set(syscall src/netlink/syscall_x86.s)
    elseif (${CMAKE_ANDROID_ARCH_ABI} STREQUAL "x86_64")
    set(syscall src/netlink/syscall_x86_64.s)
else()
    message(FATAL_ERROR "Unsupported architecture: ${CMAKE_ANDROID_ARCH_ABI}")
endif()

# 头文件目录
include_directories(
        inc/
        inc/collector/
        inc/utils/
        inc/netlink/
        inc/detector/
        inc/network/
        ${ANDROID_NDK}/sysroot/usr/include
        ${ANDROID_NDK}/sysroot/usr/include/${ANDROID_TOOLCHAIN_NAME}
)
file(GLOB CRYPTO_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/src/crypto/*.cpp")
file(GLOB UTILS_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/src/utils/*.cpp")
file(GLOB DETECTOR_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/src/detector/*.cpp")
file(GLOB COLLECTOR_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/src/collector/*.cpp")
file(GLOB NETWORK_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/src/network/*.cpp")

# 主库源文件
set(SOURCE_FILES
        src/warlockCore.cpp
        src/netlink/ifaddrs.cpp
        src/netlink/NetlinkConnection.cpp
        src/detector/SystemDetector.cpp
        src/detector/HookDetector.cpp
        ${DETECTOR_SOURCES}
        ${syscall}
        ${CRYPTO_SOURCES}
        ${UTILS_SOURCES}
        ${COLLECTOR_SOURCES}
        ${NETWORK_SOURCES}
)

# 隔离进程库源文件
set(ISOLATED_SOURCE_FILES
        src/detector/SandboxDetector.cpp
        src/warlockServer.cpp
)

# 添加两个库
add_library(warlockCore SHARED ${SOURCE_FILES})
add_library(warlockServer SHARED ${ISOLATED_SOURCE_FILES})

# 查找系统库
find_library(log-lib log)
find_library(mediandk-lib mediandk)
find_library(z-lib z)

# 配置主库
target_include_directories(warlockCore PRIVATE ${rapidjson_SOURCE_DIR}/include)
target_link_libraries(warlockCore
        ${log-lib}
        ${mediandk-lib}
        ${z-lib}
)

# 配置隔离进程库
target_link_libraries(warlockServer
        ${log-lib}
)