package com.glee.exoffmpeg.ape;

import android.support.annotation.Nullable;

import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.OutputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;

/**
 * @author liji
 * @date 10/16/2018 5:53 PM
 * description
 */


public class APEDecoder extends SimpleDecoder {

    protected APEDecoder(  int numInputBuffers,
                           int numOutputBuffers) {
        super(new DecoderInputBuffer[numInputBuffers], new SimpleOutputBuffer[numOutputBuffers]);
    }

    @Override
    protected DecoderInputBuffer createInputBuffer() {
        return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    }

    @Override
    protected SimpleOutputBuffer createOutputBuffer() {
        return new SimpleOutputBuffer(this);
    }

    @Override
    protected Exception createUnexpectedDecodeException(Throwable error) {
        return null;
    }

    @Nullable
    @Override
    protected Exception decode(DecoderInputBuffer inputBuffer, OutputBuffer outputBuffer, boolean reset) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void queueInputBuffer(Object inputBuffer) throws Exception {

    }
}
