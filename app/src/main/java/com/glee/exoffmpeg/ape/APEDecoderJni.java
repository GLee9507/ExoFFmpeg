package com.glee.exoffmpeg.ape;

/**
 * @author liji
 * @date 11/1/2018 3:06 PM
 * description
 */


public class APEDecoderJni {
    static {
        System.loadLibrary("ape_decoder");
    }

    private long pDecoder;
    private long pFormatCtx;
    private int streamIndex;

    public APEDecoderJni(long pDecoder, long pFormatCtx, int streamIndex) {
        this.pDecoder = pDecoder;
        this.streamIndex = streamIndex;
        this.pFormatCtx = pFormatCtx;
    }

    public static native APEDecoderJni sniff(String filePath);

    public native void release();
}
