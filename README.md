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

## 2 FFmpeg编译
以编译ExoPlayer FFmpeg扩展为例
``` bash
#!/usr/bin/env bash
cd "./ExoPlayer"
EXOPLAYER_ROOT="$(pwd)"

FFMPEG_EXT_PATH="${EXOPLAYER_ROOT}/extensions/ffmpeg/src/main"

#Manjaro
NDK_PATH="/home/glee/Android/android-ndk-r15c"

#WSL
#NDK_PATH="/mnt/d/ubuntu/android-ndk"

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
    --enable-decoder=flac \
    --enable-demuxer=flac \
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
    --sysroot="${NDK_PATH}/platforms/android-19/arch-arm/" \
    --extra-cflags="-march=armv7-a -mfloat-abi=softfp" \
    --extra-ldflags="-Wl,--fix-cortex-a8" \
    --extra-ldexeflags=-pie \
    ${COMMON_OPTIONS} \
    && \
make -j4 && make install && \

make clean && ./configure \
    --libdir=../android-libs/x86 \
    --arch=x86 \
    --cpu=i686 \
    --cross-prefix="${NDK_PATH}/toolchains/x86-4.9/prebuilt/${HOST_PLATFORM}/bin/i686-linux-android-" \
    --sysroot="${NDK_PATH}/platforms/android-19/arch-x86/" \
    --extra-ldexeflags=-pie \
    --disable-asm \
    ${COMMON_OPTIONS} \
    && \
make -j4 && make install-libs && \

make clean
```

## 3 Android中使用FFmpeg音频解码

### 3.1 Java层

```java
public class FFmpegTest {
    static {
        //加载so库
        System.loadLibrary("ffmpeg");
    }

    /**
     * 调用native方法，开始解码
     *
     * @param path     音乐文件路径
     * @param callback pcm数组回调
     */
    public native static void play(String path, FFmpegCallback callback);

    public interface FFmpegCallback {
        /**
         * 回调pcm数组
         *
         * @param pcm pcm数据
         */
        void pcm(byte[] pcm);
    }
}
```
### 3.2 Native层

```c
JNIEXPORT void JNICALL Java_com_google_android_exoplayer2_ext_ffmpeg_FFmpegTest_play
        (JNIEnv *env, jclass type, jstring input_jstr, jobject callback) {

    //回调方法的id
    jmethodID callMID = (*env)->GetMethodID(env,
                                            (*env)->GetObjectClass(env, callback),
                                            "pcm",
                                            "([B)V");
    //Java空间传入的String 转为数组指针
    const char *input_cstr = (*env)->GetStringUTFChars(env, input_jstr, 0);
    //注册所有组件
    av_register_all();
    //申请AVFormatContext内存空间
    AVFormatContext *pFormatCtx = avformat_alloc_context();
    //打开音频文件，并且初始化AVFormatContext
    int open = avformat_open_input(&pFormatCtx, 8, NULL, NULL);
    if (open != 0) {
        logError(open);
        return;
    }
    //获取输入流信息
    open = avformat_find_stream_info(pFormatCtx, NULL);
    if (open) {
        logError(open);
        return;
    }
    //获取音频流索引
    int i = 0, audio_stream_idx = -1;
    for (; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO) {
            audio_stream_idx = i;
            break;
        }
    }

    //获取音频解码器上下文
    AVCodecContext *codecCtx = pFormatCtx->streams[audio_stream_idx]->codec;
    //通过codec_id初始化解码器
    AVCodec *codec = avcodec_find_decoder(codecCtx->codec_id);
    if (codec == NULL) {
        logError(open);
        return;
    }
    //打开解码器
    open = avcodec_open2(codecCtx, codec, NULL);
    if (open < 0) {
        logError(open);
        return;
    }
    //申请
    AVPacket *packet = (AVPacket *) av_malloc(sizeof(AVPacket));
    //申请帧内存空间
    AVFrame *frame = av_frame_alloc();

    //frame->16bit 44100 PCM 统一音频采样格式与采样率
    //申请重采样上下文内存空间
    SwrContext *swrCtx = swr_alloc();

    //输入的采样格式
    enum AVSampleFormat in_sample_fmt = codecCtx->sample_fmt;
    //输出采样格式16bit PCM
    enum AVSampleFormat out_sample_fmt = AV_SAMPLE_FMT_S16;
    //输入采样率
    int in_sample_rate = codecCtx->sample_rate;
    //输出采样率
    int out_sample_rate = 44100;
    //输入声道布局
    uint64_t in_ch_layout = codecCtx->channel_layout;
    //输出的声道布局（立体声）
    uint64_t out_ch_layout = AV_CH_LAYOUT_STEREO;
    //应用重采样上下文的设置
    swr_alloc_set_opts(swrCtx,
                       out_ch_layout, out_sample_fmt, out_sample_rate,
                       in_ch_layout, in_sample_fmt, in_sample_rate,
                       0, NULL);
    //初始化重采样
    swr_init(swrCtx);
    //输出的声道个数
    int out_channel_nb = av_get_channel_layout_nb_channels(out_ch_layout);
    //输出缓冲区
    uint8_t *out_buffer = (uint8_t *) av_malloc(MAX_AUDIO_FRAME_SIZE);
    int got_frame = 0, index = 0, ret;
    //循环读帧
    while (av_read_frame(pFormatCtx, packet) >= 0) {
        //解码音频类型的Packet
        if (packet->stream_index == audio_stream_idx) {
            //解码
            ret = avcodec_decode_audio4(codecCtx, frame, &got_frame, packet);
            if (ret < 0) {
                logError(ret);
                break;
            }
            //解码一帧成功
            if (got_frame > 0) {
                //执行重采样
                swr_convert(swrCtx, &out_buffer, MAX_AUDIO_FRAME_SIZE,
                            (const uint8_t **) frame->data, frame->nb_samples);
                // 获取sample的size
                int out_buffer_size = av_samples_get_buffer_size(NULL, out_channel_nb,
                                                                 frame->nb_samples, out_sample_fmt,
                                                                 1);
                // out_buffer缓冲区数据，转成byte数组
                //new Java空间的字节数组
                jbyteArray audio_sample_array = (*env)->NewByteArray(env, out_buffer_size);
                //获取audio_sample_array的指针
                jbyte *sample_bytep = (*env)->GetByteArrayElements(env, audio_sample_array, NULL);
                // out_buffer的数据拷贝到sampe_bytep
                memcpy(sample_bytep, out_buffer, out_buffer_size);

                // 回调 PCM数据
                (*env)->CallVoidMethod(env, callback, callMID, audio_sample_array);
                //释放pcm数组
                (*env)->ReleaseByteArrayElements(env, audio_sample_array, sample_bytep, 0);
                // 释放局部引用
                (*env)->DeleteLocalRef(env, audio_sample_array);
                //挂起16毫秒
                usleep(1000 * 16);
            }
        }
        //释放AVPacket
        av_free_packet(packet);
    }

    //释放资源
    av_frame_free(&frame);
    av_free(out_buffer);
    swr_free(&swrCtx);
    avcodec_close(codecCtx);
    avformat_close_input(&pFormatCtx);
    (*env)->ReleaseStringUTFChars(env, input_jstr, input_cstr);

}

```

### 3.3 例

```java
new Thread(new Runnable() {
    @Override
    public void run() {
        //在子线程中执行
        FFmpegTest.play(
                //音乐文件路径
                Environment.getExternalStorageDirectory().getPath() + "/Music/Kalimba.flac",
                //解码后的数据回调
                new FFmpegTest.FFmpegCallback() {
                    @Override
                    public void pcm(byte[] pcm) {
                        Log.d(TAG, pcm.length + "");
                    }
                });
    }
}).start();
```