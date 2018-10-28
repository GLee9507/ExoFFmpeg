


#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <libavutil/intreadwrite.h>


#define LOG_TAG "ffmpeg"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                   __VA_ARGS__))
#define MAX_AUDIO_FRAME_SIZE (48000 * 4)
#define AUDIO_INBUF_SIZE 20480
#define AUDIO_REFILL_THRESH 4096
char *error;

void logError(int errorNum) {

    av_make_error_string(error, 64, errorNum);
    LOGE("%s", error);
    free(error);
}

JNIEXPORT void JNICALL Java_com_google_android_exoplayer2_ext_ffmpeg_FFmpegTest_play
        (JNIEnv *env, jclass type, jstring input_jstr, jobject callback) {

    //回调方法的id
    error = malloc(64);
    jmethodID callMID = (*env)->GetMethodID(env,
                                            (*env)->GetObjectClass(env, callback),
                                            "pcm",
                                            "([B)V");
    //Java空间传入的String 转为数组指针
    const char *input_cstr = (*env)->GetStringUTFChars(env, input_jstr, 0);
    const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_APE);
//    AVFormatContext *af = avformat_alloc_context();
    AVCodecContext *codecContext = avcodec_alloc_context3(codec);
    int ret;
    int16_t fileversion =3990;

    codecContext->extradata_size = 6;

    codecContext->extradata = malloc((size_t) codecContext->extradata_size);
    AV_WL16(codecContext->extradata,fileversion);
    AV_WL16(codecContext->extradata+2,0);
    AV_WL16(codecContext->extradata+4,0);
    codecContext->sample_rate = 44100;
    codecContext->channels=2;
    codecContext->sample_fmt = AV_SAMPLE_FMT_S16P;
    codecContext->channel_layout = 3;
    codecContext->bit_rate_tolerance=4000000;
    codecContext->bits_per_coded_sample=16;
    codecContext->compression_level = -1;
    codecContext->gop_size = 12;

    codecContext-> mb_lmin =  236;
    codecContext-> mb_lmax =  3658;
    codecContext-> me_penalty_compensation =  256;
    codecContext-> bidir_refine =  1;
    codecContext->brd_scale =0;
    codecContext->keyint_min =  25;
    codecContext-> refs =  1;
    codecContext->chromaoffset = 0;
    codecContext-> mv0_threshold =  256;
    codecContext-> b_sensitivity =  40;
    if (!codec) {
        return;
    }
//    AVCodecParserContext *parser = av_parser_init(codec->id);
//    if (!parser) {
//        logError(parser);
//        return;
//    }
//int ret = 0;
    if (!codecContext) {
        return;
    }
    /* open it */

    if ((ret = avcodec_open2(codecContext, codec, NULL)) < 0) {
        logError(ret);
        return;
    }
//    AVDictionaryEntry *tag = NULL;
//    while ((tag = av_dict_get(af->metadata, "", tag, AV_DICT_IGNORE_SUFFIX)))
//        LOGE("%s=%s\n", tag->key, tag->value);

    FILE *f = fopen(input_cstr, "rb");
    if (!f) {
        return;
    }
    uint8_t inbuf[AUDIO_INBUF_SIZE + AV_INPUT_BUFFER_PADDING_SIZE];
    /* decode until eof */
    uint8_t *data = inbuf;
    size_t data_size = fread(inbuf, 1, AUDIO_INBUF_SIZE, f);
}

