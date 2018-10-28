cmake_minimum_required(VERSION 3.4.1)

add_library( # Sets the name of the library.
        fftest
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        ${CMAKE_SOURCE_DIR}/src/FFmpegTestDecode.c )

find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log )
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

add_library(libavutil SHARED IMPORTED)

set_target_properties(
        libavutil
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/android-libs/${ANDROID_ABI}/libavutil.so
)

add_library(libavcodec SHARED IMPORTED)
set_target_properties(
        libavcodec
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/android-libs/${ANDROID_ABI}/libavcodec.so
)

add_library(libavformat SHARED IMPORTED)
set_target_properties(
        libavformat
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/android-libs/${ANDROID_ABI}/libavformat.so
)

add_library(libswresample SHARED IMPORTED)
set_target_properties(
        libswresample
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/android-libs/${ANDROID_ABI}/libswresample.so
)

target_link_libraries( # Specifies the target library.
        fftest
        # Links the target library to the log library
        # included in the NDK.
        libavutil
        libavcodec
        libavformat
        libswresample
        ${log-lib} )

