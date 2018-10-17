package com.glee.exoffmpeg;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.glee.AudioOnlyRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.ffmpeg.FFmpegTest;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

public class MainActivity extends AppCompatActivity {
    private Context context;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                //在子线程中执行
//                FFmpegTest.play(
//                        //音乐文件路径
//                        Environment.getExternalStorageDirectory().getPath() + "/Music/Kalimba.flac",
//                        //解码后的数据回调
//                        new FFmpegTest.FFmpegCallback() {
//                            @Override
//                            public void pcm(byte[] pcm) {
//                                Log.d(TAG, pcm.length + "");
//                            }
//                        });
//            }
//        }).start();
        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(context);
        ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(context,
                Util.getUserAgent(context, "yourApplicationName")))
                .createMediaSource(Uri.parse(Environment.getExternalStorageDirectory().getPath() + "/Music/Kalimba.flac"));
        player.setPlayWhenReady(true);
        player.prepare(mediaSource);
    }
}
