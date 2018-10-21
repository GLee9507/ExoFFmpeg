package com.glee.exoffmpeg.ape;

import com.glee.exoffmpeg.util.ExtractorInputWrapper;

import java.io.IOException;

/**
 * Author: Dmitry Vaguine
 * Date: 04.03.2004
 * Time: 14:51:31
 */
public class APEHeaderOld {
    public String cID;                    // should equal 'MAC '
    public int nVersion;                // version number * 1000 (3.81 = 3810)
    public int nCompressionLevel;        // the compression level
    public int nFormatFlags;            // any format flags (for future use)
    public int nChannels;                // the number of channels (1 or 2)
    public long nSampleRate;            // the sample rate (typically 44100)
    public long nHeaderBytes;            // the bytes after the MAC header that compose the WAV header
    public long nTerminatingBytes;        // the bytes after that raw data (for extended info)
    public long nTotalFrames;            // the number of frames in the file
    public long nFinalFrameBlocks;        // the number of samples in the final frame

    public final static int APE_HEADER_OLD_BYTES = 32;

    public static APEHeaderOld read(final ExtractorInputWrapper input) throws IOException, InterruptedException {
        input.resetPeekPosition();
        APEHeaderOld header = new APEHeaderOld();
        header.cID = input.peekString(4, "US-ASCII");
        header.nVersion = input.peekUnsignedShort();
        header.nCompressionLevel = input.peekUnsignedShort();
        header.nFormatFlags = input.peekUnsignedShort();
        header.nChannels = input.peekUnsignedShort();
        header.nSampleRate = input.peekUnsignedInt();
        header.nHeaderBytes = input.peekUnsignedInt();
        header.nTerminatingBytes = input.peekUnsignedInt();
        header.nTotalFrames = input.peekUnsignedInt();
        header.nFinalFrameBlocks = input.peekUnsignedInt();
        return header;
    }
}