package com.glee.exoffmpeg.ape;

import com.glee.exoffmpeg.util.ExtractorInputWrapper;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.IOException;

import static com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO;
import static com.google.android.exoplayer2.util.Util.getPcmEncoding;

/**
 * @author liji
 * @date 10/16/2018 3:26 PM
 * description APE 提取器
 */


public class APEExtractor implements Extractor {
    /**
     * APE读取器
     */
    private APEReader apeReader;
    /**
     * APE解码器
     */
    private APEDecoder decoder;
    /**
     * APE文件信息
     */
    private APEFileInfo apeFileInfo;
    /**
     * 输出轨道
     */
    private TrackOutput track;

    @Override
    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return (apeReader = APEReader.sniff(new ExtractorInputWrapper(input))) != null;
    }

    @Override
    public void init(ExtractorOutput output) {
        track = output.track(0, TRACK_TYPE_AUDIO);
        decoder = new APEDecoder(4096, 4096);
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        readApeInfo();
        //TODO 解码未实现
        return 0;
    }

    private void readApeInfo() throws IOException, InterruptedException {
        if (apeFileInfo != null) {
            return;
        }
        apeFileInfo = apeReader.read();
        track.format(Format.createAudioSampleFormat(
                /* id= */ null,
                MimeTypes.AUDIO_RAW,
                /* codecs= */ null,
                apeFileInfo.nAverageBitrate,
                Format.NO_VALUE,
                apeFileInfo.nChannels,
                apeFileInfo.nSampleRate,
                getPcmEncoding(apeFileInfo.nBitsPerSample),
                /* encoderDelay= */ 0,
                /* encoderPadding= */ 0,
                /* initializationData= */ null,
                /* drmInitData= */ null,
                /* selectionFlags= */ 0,
                /* language= */ null,
                /* metadata= */ null)
        );

    }

    @Override
    public void seek(long position, long timeUs) {
        //TODO
    }

    @Override
    public void release() {
        decoder.release();
    }
}
