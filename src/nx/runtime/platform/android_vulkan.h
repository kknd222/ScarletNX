/* SPDX-License-Identifier: GPL-3.0-or-later
   Copyright 2026 the ScarletNX contributors */
#ifndef SNX_ANDROID_VULKAN_H
#define SNX_ANDROID_VULKAN_H
/* Called after SDL video init and before its first Vulkan window. System driver is the default. */
void snx_android_vulkan_prepare(void);
#endif
