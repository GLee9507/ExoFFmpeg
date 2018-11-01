//
// Created by P on 11/1/2018.
//

#include <jni.h>
#include <android/log.h>
#include <libavformat/avformat.h>

#ifndef EXOFFMPEG_APE_DECODER_H
#define EXOFFMPEG_APE_DECODER_H
#endif //EXOFFMPEG_APE_DECODER_H


#define LOG_TAG "ape_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                   __VA_ARGS__))

jobject sniff(JNIEnv *env, jclass clazz, jstring j_str);

void release(JNIEnv *env, jobject object);

static JNINativeMethod methods[] = {
        {"sniff",   "(Ljava/lang/String;)Lcom/glee/exoffmpeg/ape/APEDecoderJni;", (void *) sniff},
        {"release", "()V",                                                        (void *) release}
};
jclass cls = NULL;
JNIEnv *senv = NULL;
jfieldID pFormatCtxID = NULL;
jfieldID streamIndexID = NULL;

int init() {
    cls = (*senv)->FindClass(senv, "com/glee/exoffmpeg/ape/APEDecoderJni");
    (*senv)->RegisterNatives(senv, cls, methods, sizeof(methods) / sizeof(JNINativeMethod));
    av_register_all();
    pFormatCtxID = (*senv)->GetFieldID(senv, cls, "pFormatCtx", "J");
    streamIndexID = (*senv)->GetFieldID(senv, cls, "streamIndex", "I");
    return 1;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    jint result = JNI_VERSION_1_6;
    if ((*vm)->AttachCurrentThread(vm, &senv, NULL) != JNI_OK) {
        LOGE("ape_jni error");
        return JNI_ERR;
    }
    init();

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    (*senv)->UnregisterNatives(senv, cls);
}


