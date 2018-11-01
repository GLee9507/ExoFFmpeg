package com.glee.exoffmpeg.ape;

import android.view.Surface;

/**
 * @author liji
 * @date 11/1/2018 3:06 PM
 * description
 */


public class APEDecoderJni {
    static {
        System.loadLibrary("ape_decoder");
    }

    private long pCodec;
    private long pCodecCtx;
    private long pFormatCtx;
    private int streamIndex;

    private APEDecoderJni(long pCodec, long pCodecCtx, long pFormatCtx, int streamIndex) {
        this.pCodec = pCodec;
        this.pCodecCtx = pCodecCtx;
        this.streamIndex = streamIndex;
        this.pFormatCtx = pFormatCtx;
    }

    public static native APEDecoderJni sniff(String filePath);

    public native void release();
}
