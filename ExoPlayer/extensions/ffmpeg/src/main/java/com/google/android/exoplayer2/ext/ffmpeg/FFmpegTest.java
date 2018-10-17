package com.google.android.exoplayer2.ext.ffmpeg;

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
