//
// Created by P on 11/1/2018.
//

#include "ape_decoder.h"


jobject sniff(JNIEnv *env, jclass clazz, jstring j_str) {
    LOGE("0");
    jboolean copy = 0;
    LOGE("1");
    const char *c_str = (*env)->GetStringUTFChars(env, j_str, &copy);
    LOGE("2");

    int ret = -1;
    LOGE("3");
    AVFormatContext *pFormatCtx = avformat_alloc_context();
    AVCodec *pCodec = NULL;
    LOGE("4");
    ret = avformat_open_input(&pFormatCtx, c_str, NULL, NULL);
    if (ret)
        return NULL;
    LOGE("5");
    ret = avformat_find_stream_info(pFormatCtx, NULL);
    if (ret)
        return NULL;
    LOGE("6");
    int streamIndex = av_find_best_stream(pFormatCtx, AVMEDIA_TYPE_AUDIO, -1, -1, &pCodec, 0);
    LOGE("7");
    jmethodID mid = (*env)->GetMethodID(env, clazz, "<init>", "(JJI)V");
    LOGE("8");
    jobject object = (*env)->NewObject(env, clazz, mid, (int64_t) pCodec, (int64_t) pFormatCtx,
                                       streamIndex);
    LOGE("9");
    avformat_close_input(&pFormatCtx);
    return object;
}

void release(JNIEnv *env, jobject object) {
    AVFormatContext *formatContext = (AVFormatContext *) (*env)->GetLongField(env, object, pFormatCtxID);
    avformat_free_context(formatContext);
    (*env)->DeleteGlobalRef(env, object);
}