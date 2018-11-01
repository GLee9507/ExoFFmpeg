//
// Created by P on 11/1/2018.
//

#include "ape_decoder.h"


jobject sniff(JNIEnv *env, jclass clazz, jstring j_str) {
    jboolean copy = 0;
    const char *c_str = (*env)->GetStringUTFChars(env, j_str, &copy);
    int ret = -1;
    LOGE("0");
    AVFormatContext *pFormatCtx = avformat_alloc_context();
    AVCodec *pCodec = NULL;
    AVCodecContext *pCodecCtx = NULL;
    LOGE("1");
    ret = avformat_open_input(&pFormatCtx, c_str, NULL, NULL);
    if (ret != 0) {
        logError(ret);
        return NULL;
    }
    LOGE("2");
    ret = avformat_find_stream_info(pFormatCtx, NULL);
    if (ret != 0) {
        logError(ret);
        return NULL;
    }
    LOGE("3");
    int streamIndex = av_find_best_stream(pFormatCtx, AVMEDIA_TYPE_AUDIO, -1, -1, &pCodec, 0);
    if (!pCodec) {
        return NULL;
    }
    LOGE("4");
    pCodecCtx = avcodec_alloc_context3(pCodec);
    if (!pCodecCtx) {
        return NULL;
    }
    LOGE("%64d",pCodecCtx);
    avformat_close_input(&pFormatCtx);
    LOGE("6");
    (*env)->ReleaseStringUTFChars(env, j_str, c_str);
    LOGE("7");
    (*env)->DeleteLocalRef(env, j_str);
    LOGE("8");
    return (*env)->NewObject(env, clazz, initID,
            (jlong)(intptr_t) pCodec,
            (jlong)(intptr_t)pCodecCtx,
            (jlong)(intptr_t)pFormatCtx,
            (jlong)(intptr_t)streamIndex);
}

void release(JNIEnv *env, jobject object) {
    avformat_free_context((AVFormatContext *) (*env)->GetLongField(env, object, pFormatCtxID));
    AVCodecContext *pCodecCtx = (AVCodecContext *) (*env)->GetLongField(env, object, pCodecCtxID);
    avcodec_free_context(&pCodecCtx);
    (*env)->DeleteLocalRef(env, object);
}