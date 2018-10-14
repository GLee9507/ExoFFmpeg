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
make -j4 && make install && \


make clean