


#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
#include <jni.h>
#include <android/log.h>
#include <unistd.h>


#define LOG_TAG "ffmpeg"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                   __VA_ARGS__))
#define MAX_AUDIO_FRAME_SIZE (48000 * 4)
char *error;

void logError(int errorNum) {
    av_make_error_string(error, 64, errorNum);
    LOGE("%s", error);
    free(error);
}

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
    int open = avformat_open_input(&pFormatCtx, input_cstr, NULL, NULL);
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

