package com.glee.exoffmpeg.ape;

import com.glee.exoffmpeg.util.ExtractorInputWrapper;

import java.io.IOException;

/**
 * Author: Dmitry Vaguine
 * Date: 04.03.2004
 * Time: 14:51:31
 */
public class APEHeaderNew {
    public int nCompressionLevel;        // the compression level (unsigned short)
    public int nFormatFlags;            // any format flags (for future use) (unsigned short)

    public long nBlocksPerFrame;        // the number of audio blocks in one frame (unsigned int)
    public long nFinalFrameBlocks;        // the number of audio blocks in the final frame (unsigned int)
    public long nTotalFrames;            // the total number of frames (unsigned int)

    public int nBitsPerSample;            // the bits per sample (typically 16) (unsigned short)
    public int nChannels;                // the number of channels (1 or 2) (unsigned short)
    public long nSampleRate;            // the sample rate (typically 44100) (unsigned int)

    public final static int APE_HEADER_BYTES = 24;

    public static APEHeaderNew read(final ExtractorInputWrapper input) throws IOException, InterruptedException {
        APEHeaderNew header = new APEHeaderNew();
        header.nCompressionLevel = input.readUnsignedShort();
        header.nFormatFlags = input.readUnsignedShort();
        header.nBlocksPerFrame = input.readUnsignedInt();
        header.nFinalFrameBlocks = input.readUnsignedInt();
        header.nTotalFrames = input.readUnsignedInt();
        header.nBitsPerSample = input.readUnsignedShort();
        header.nChannels = input.readUnsignedShort();
        header.nSampleRate = input.readUnsignedInt();
        return header;
    }

}