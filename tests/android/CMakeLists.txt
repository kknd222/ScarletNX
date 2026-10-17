# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright 2026 the ScarletNX contributors
# A fast NDK build of the loader and JNI bridge before the full LLVM/runtime build.
cmake_minimum_required(VERSION 3.20)
project(ScarletNXDriverCheck LANGUAGES C CXX)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(PROJECT_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../..")
set(SNX_BUILD_NX ON)
set(SNX_TOOLS_ONLY OFF)
set(CPM_VERSION 0.43.2)
set(cpm_file "${CMAKE_BINARY_DIR}/CPM.cmake")
file(DOWNLOAD "https://github.com/cpm-cmake/CPM.cmake/releases/download/v${CPM_VERSION}/CPM.cmake"
     "${cpm_file}" EXPECTED_HASH SHA256=49a3bef91ceb65bb66d57255e12d1ffd22abc2f6408fa9fe4534c544a2f232aa)
include("${cpm_file}")
CPMAddPackage(NAME Vulkan-Headers
    URL https://github.com/KhronosGroup/Vulkan-Headers/archive/refs/tags/v1.4.365.tar.gz
    URL_HASH SHA256=ed832b3292cce324ebba6522fd2d3004bf1b797fa49bfbcad0b302ff23d56abc
    EXCLUDE_FROM_ALL YES)
CPMAddPackage(NAME SDL3_headers
    URL https://github.com/libsdl-org/SDL/archive/refs/tags/release-3.4.18.tar.gz
    URL_HASH SHA256=c4b08b950bd29d83caae0ab8d884298d27046a9d30700fb42841c50992665c90
    DOWNLOAD_ONLY YES)
include("${PROJECT_SOURCE_DIR}/cmake/android_vulkan.cmake")
add_library(snx-driver-jni OBJECT "${PROJECT_SOURCE_DIR}/src/nx/runtime/platform/android_vulkan.c")
target_compile_definitions(snx-driver-jni PRIVATE SNX_ADRENOTOOLS)
target_include_directories(snx-driver-jni PRIVATE "${PROJECT_SOURCE_DIR}/src" "${SDL3_headers_SOURCE_DIR}/include")
target_compile_options(snx-driver-jni PRIVATE -Wall -Wextra -Werror)
