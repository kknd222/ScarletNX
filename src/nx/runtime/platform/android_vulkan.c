/* SPDX-License-Identifier: GPL-3.0-or-later
   Copyright 2026 the ScarletNX contributors */
#include "nx/runtime/platform/android_vulkan.h"
#include "nx/runtime/platform/log.h"
#include <SDL3/SDL.h>
#include <SDL3/SDL_vulkan.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdio.h>

void snx_android_vulkan_prepare(void) {
#ifdef SNX_ADRENOTOOLS
    JNIEnv* env = (JNIEnv*)SDL_GetAndroidJNIEnv();
    jobject activity = (jobject)SDL_GetAndroidActivity();
    jclass cls = NULL;
    jobjectArray config = NULL;
    jstring strings[4] = {NULL};
    const char* paths[4] = {NULL};
    int loaded = 0;
    if (!env || !activity) return;
    cls = (*env)->GetObjectClass(env, activity);
    jmethodID method = cls ? (*env)->GetMethodID(env, cls, "gpuDriverConfig", "()[Ljava/lang/String;") : NULL;
    if (method) config = (jobjectArray)(*env)->CallObjectMethod(env, activity, method);
    if ((*env)->ExceptionCheck(env)) goto done;
    if (!config || (*env)->GetArrayLength(env, config) != 4) goto done;
    for (int i = 0; i < 4; ++i) {
        strings[i] = (jstring)(*env)->GetObjectArrayElement(env, config, i);
        if (!strings[i]) goto done;
        paths[i] = (*env)->GetStringUTFChars(env, strings[i], NULL);
        if (!paths[i]) goto done;
    }
    char library[4096];
    int n = snprintf(library, sizeof library, "%s/libsnx-vulkan.so", paths[0]);
    if (n < 0 || (size_t)n >= sizeof library) goto done;
    /* Keep the proxy and its namespace alive for the whole process, including surface recreation. */
    void* proxy = dlopen(library, RTLD_NOW | RTLD_LOCAL);
    int (*configure)(const char*, const char*, const char*, const char*) =
        proxy ? (int (*)(const char*, const char*, const char*, const char*))dlsym(proxy, "snx_vulkan_configure")
              : NULL;
    if (configure && configure(paths[0], paths[1], paths[2], paths[3])) {
        loaded = SDL_Vulkan_LoadLibrary(library);
        if (!loaded) WARN("vulkan: custom loader rejected by SDL: %s\n", SDL_GetError());
    }
    if (loaded)
        LOG("vulkan: using custom driver %s; GPU/device validation follows\n", paths[3]);
    else
        WARN("vulkan: custom driver could not load; falling back to system driver\n");
done:
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        WARN("vulkan: could not read Android driver selection; using system driver\n");
    }
    for (int i = 0; i < 4; ++i) {
        if (paths[i]) (*env)->ReleaseStringUTFChars(env, strings[i], paths[i]);
        if (strings[i]) (*env)->DeleteLocalRef(env, strings[i]);
    }
    if (config) (*env)->DeleteLocalRef(env, config);
    if (cls) (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
#endif
}
