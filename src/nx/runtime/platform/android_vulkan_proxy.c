/* SPDX-License-Identifier: GPL-3.0-or-later
   Copyright 2026 the ScarletNX contributors */
/* SDL loads this library by absolute path. Both SDL's surface code and Volk then use this GIPA.
   Do not link it into the runtime: Volk has a global variable named vkGetInstanceProcAddr. */
#include <adrenotools/driver.h>
#include <android/log.h>
#include <dlfcn.h>
#include <vulkan/vulkan.h>

#define EXPORT __attribute__((visibility("default")))
static PFN_vkGetInstanceProcAddr driver_gipa;
static void* driver_handle; /* pinned until GameActivity's process exits; no live driver may be dlclosed */

EXPORT int snx_vulkan_configure(const char* hooks, const char* temporary, const char* directory, const char* name);
EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char* name);

EXPORT int snx_vulkan_configure(const char* hooks, const char* temporary, const char* directory, const char* name) {
    if (driver_gipa) return 1;
    driver_handle = adrenotools_open_libvulkan(RTLD_NOW | RTLD_LOCAL, ADRENOTOOLS_DRIVER_CUSTOM, temporary, hooks,
                                               directory, name, NULL, NULL);
    if (driver_handle) driver_gipa = (PFN_vkGetInstanceProcAddr)dlsym(driver_handle, "vkGetInstanceProcAddr");
    if (!driver_gipa) {
        __android_log_print(ANDROID_LOG_ERROR, "ScarletNX", "Custom Vulkan loader failed for %s", name);
        return 0;
    }
    __android_log_print(ANDROID_LOG_INFO, "ScarletNX", "Custom Vulkan loader opened: %s", name);
    return 1;
}

EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char* name) {
    return driver_gipa ? driver_gipa(instance, name) : NULL;
}
