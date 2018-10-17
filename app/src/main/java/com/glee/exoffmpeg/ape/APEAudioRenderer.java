package com.glee.exoffmpeg.ape;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioDecoderException;
import com.google.android.exoplayer2.audio.SimpleDecoderAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;

/**
 * @author liji
 * @date 10/16/2018 5:45 PM
 * description
 */


public class APEAudioRenderer extends SimpleDecoderAudioRenderer {
    @Override
    protected int supportsFormatInternal(DrmSessionManager<ExoMediaCrypto> drmSessionManager, Format format) {
        return 0;
    }

    @Override
    protected SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer, ? extends AudioDecoderException> createDecoder(Format format, ExoMediaCrypto mediaCrypto) throws AudioDecoderException {
        return null;
    }

    @Override
    public void setOperatingRate(float operatingRate) {

    }
}
