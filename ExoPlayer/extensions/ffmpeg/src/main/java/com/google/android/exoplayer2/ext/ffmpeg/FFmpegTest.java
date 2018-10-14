package com.google.android.exoplayer2.ext.ffmpeg;

public class FFmpegTest {
    static {
        System.loadLibrary("ffmpeg");
    }

    public native static void play(String path, FFmpegCallback callback);

    public interface FFmpegCallback {
        void pcm(byte[] pcm);
    }
}
