# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright 2026 the ScarletNX contributors
# Eden's pinned libadrenotools, with its separately pinned submodule (archives exclude submodules).
# Other ABIs and API 21-27 retain SDL's normal system Vulkan path.
if(ANDROID AND ANDROID_ABI STREQUAL "arm64-v8a" AND SNX_BUILD_NX AND NOT SNX_TOOLS_ONLY)
    CPMAddPackage(NAME snx_linkernsbypass
        URL https://github.com/bylaws/liblinkernsbypass/archive/aa3975893d83ef1bc84c321ec60c65fbf1287887.tar.gz
        URL_HASH SHA256=da1128c8aa771c4d24766b53a47822d0717baa5c536ca8491220402942b80638
        EXCLUDE_FROM_ALL YES)
    CPMAddPackage(NAME snx_adrenotools_source
        URL https://github.com/eden-emulator/libadrenotools/archive/8ba23b42d742545b709064d6e2523cdb86de68f5.tar.gz
        URL_HASH SHA256=b62509e200f0ff3af45c96cfbc491abbacc9a6517665d8134b921f294be8b7df
        DOWNLOAD_ONLY YES)
    # Build the static core explicitly so no CPM cache is patched to insert a submodule.
    add_library(snx_adrenotools STATIC "${snx_adrenotools_source_SOURCE_DIR}/src/driver.cpp"
                                     "${snx_adrenotools_source_SOURCE_DIR}/src/bcenabler.cpp")
    target_include_directories(snx_adrenotools PUBLIC "${snx_adrenotools_source_SOURCE_DIR}/include"
                                           PRIVATE "${snx_adrenotools_source_SOURCE_DIR}")
    target_link_libraries(snx_adrenotools PUBLIC android linkernsbypass)
    add_subdirectory("${snx_adrenotools_source_SOURCE_DIR}/src/hook" "${CMAKE_BINARY_DIR}/adrenotools-hooks")
    set_target_properties(snx_adrenotools linkernsbypass PROPERTIES POSITION_INDEPENDENT_CODE ON)
    add_library(snx-vulkan SHARED "${PROJECT_SOURCE_DIR}/src/nx/runtime/platform/android_vulkan_proxy.c")
    target_compile_definitions(snx-vulkan PRIVATE VK_NO_PROTOTYPES)
    target_link_libraries(snx-vulkan PRIVATE snx_adrenotools Vulkan::Headers log ${CMAKE_DL_LIBS})
    set_target_properties(snx-vulkan PROPERTIES C_VISIBILITY_PRESET hidden)
    add_dependencies(snx-vulkan main_hook hook_impl file_redirect_hook gsl_alloc_hook)
    install(TARGETS snx-vulkan main_hook hook_impl file_redirect_hook gsl_alloc_hook
            LIBRARY DESTINATION lib/${ANDROID_ABI})
    install(FILES "${snx_adrenotools_source_SOURCE_DIR}/LICENSE"
            DESTINATION share/licenses/scarletnx RENAME libadrenotools.txt)
    install(FILES "${snx_linkernsbypass_SOURCE_DIR}/LICENSE"
            DESTINATION share/licenses/scarletnx RENAME liblinkernsbypass.txt)
endif()
