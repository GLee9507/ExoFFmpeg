# FFmpeg音频处理简述

**FFmpeg**是一个完整的跨平台解决方案，用于录制，转换和流式传输音频和视频。


[TOC]

## 1 FFmpeg Libraries
### 1.1 libavutil
libavutil库是一个实用程序库，用于辅助便携式多媒体编程。
它包含安全的可移植字符串函数，随机数生成器，数据结构，附加数学函数，加密和多媒体相关功能（如像素和样本格式的枚举）。
它不是libavcodec和libavformat都需要的代码库。
### 1.2 libswscale
libswscale库执行高度优化的图像缩放以及色彩空间和像素格式转换操作。
### 1.3 libswresample
libswresample库执行高度优化的音频重采样，重新矩阵化和样本格式转换操作。
### 1.4 libavcodec
libavcodec库提供通用编码/解码框架，包含用于音频，视频和字幕流的多个解码器和编码器，以及多个比特流过滤器。
### 1.5 libavformat
libavformat库为音频，视频和字幕流的多路复用和多路分解提供了通用框架。它包含多个用于多媒体容器格式的复用器和分路器。
### 1.6 libavdevice
libavdevice库提供了一个通用框架，用于从许多常见的多媒体输入/输出设备中获取和呈现，并支持多种输入和输出设备，包括Video4Linux2，VfW，DShow和ALSA。
### 1.7 libavfilter
libavfilter库提供了一个通用的音频/视频过滤框架，其中包含多个过滤器，源和接收器。

## 1 FFmpeg编译
以编译ExoPlayer FFmpeg扩展为例
``` bash
cd "./ExoPlayer"
EXOPLAYER_ROOT="$(pwd)"
FFMPEG_EXT_PATH="${EXOPLAYER_ROOT}/extensions/ffmpeg/src/main"
NDK_PATH="/home/glee/Android/android-ndk-r15c/"
HOST_PLATFORM="linux-x86_64"
COMMON_OPTIONS="\
    --target-os=android \
    --disable-static \
    --enable-shared \
    --disable-doc \
    --disable-programs \
    --disable-everything \
    --disable-avdevice \
    --enable-avformat \
    --disable-swscale \
    --disable-postproc \
    --disable-avfilter \
    --disable-symver \
    --disable-swresample \
    --enable-avresample \
    --enable-decoder=vorbis \
    --enable-decoder=opus \
    --enable-decoder=flac \
    --enable-decoder=ape \
    " && \
cd "${FFMPEG_EXT_PATH}/jni" && \
(git -C ffmpeg pull || git clone git://source.ffmpeg.org/ffmpeg ffmpeg) && \
cd ffmpeg && \
./configure \
    --libdir=../android-libs/armeabi-v7a \
    --incdir=../include \
    --arch=arm \
    --cpu=armv7-a \
    --cross-prefix="${NDK_PATH}/toolchains/arm-linux-androideabi-4.9/prebuilt/${HOST_PLATFORM}/bin/arm-linux-androideabi-" \
    --sysroot="${NDK_PATH}/platforms/android-9/arch-arm/" \
    --extra-cflags="-march=armv7-a -mfloat-abi=softfp" \
    --extra-ldflags="-Wl,--fix-cortex-a8" \
    --extra-ldexeflags=-pie \
    ${COMMON_OPTIONS} \
    && \
make -j4 && make install && make clean
```
