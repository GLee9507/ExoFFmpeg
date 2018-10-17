#!/usr/bin/env bash
cd "./ExoPlayer"
EXOPLAYER_ROOT="$(pwd)"
echo "$EXOPLAYER_ROOT"
FLAC_EXT_PATH="${EXOPLAYER_ROOT}/extensions/flac/src/main"

#Manjaro
#NDK_PATH="/home/glee/Android/android-ndk-r15c"

#WSL
NDK_PATH="/mnt/d/ubuntu/android-ndk"
#cd "${FLAC_EXT_PATH}/jni" && \
#curl https://ftp.osuosl.org/pub/xiph/releases/flac/flac-1.3.1.tar.xz | tar xJ && \
#mv flac-1.3.1 flac

cd "${FLAC_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j4 APP_PLATFORM=android-16
