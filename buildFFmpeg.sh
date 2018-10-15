#!/usr/bin/env bash
cd "./ExoPlayer"
EXOPLAYER_ROOT="$(pwd)"

FFMPEG_EXT_PATH="${EXOPLAYER_ROOT}/extensions/ffmpeg/src/main"

#Manjaro
#NDK_PATH="/home/glee/Android/android-ndk-r15c"

#WSL
NDK_PATH="/mnt/d/ubuntu/android-ndk"

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
    --enable-swresample \

    --enable-decoder=ape \
    --enable-demuxer=ape \
    --enable-decoder=mp3 \
    --enable-demuxer=mp3 \
    --enable-protocol=file \
    " && \
cd "${FFMPEG_EXT_PATH}/jni" && \

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
#make -j4 && make install && \

make clean && ./configure \
    --libdir=../android-libs/x86 \
    --arch=x86 \
    --cpu=i686 \
    --cross-prefix="${NDK_PATH}/toolchains/x86-4.9/prebuilt/${HOST_PLATFORM}/bin/i686-linux-android-" \
    --sysroot="${NDK_PATH}/platforms/android-9/arch-x86/" \
    --extra-ldexeflags=-pie \
    --disable-asm \
    ${COMMON_OPTIONS} \
    && \
make -j4 && make install-libs && \

make clean