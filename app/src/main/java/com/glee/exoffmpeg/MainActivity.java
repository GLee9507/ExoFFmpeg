package com.glee.exoffmpeg;

import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.google.android.exoplayer2.ext.ffmpeg.FFmpegTest;
import com.google.android.exoplayer2.util.Log;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(() ->
                FFmpegTest.play(Environment.getExternalStorageDirectory().getPath()+"/Music/Kalimba.flac",
                pcm -> Log.d("glee9507",pcm.length+""))).start();

    }
}
