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
        APEHeaderOld header = new APEHeaderOld();
        header.cID = input.readString(4, "US-ASCII");
        header.nVersion = input.readUnsignedShort();
        header.nCompressionLevel = input.readUnsignedShort();
        header.nFormatFlags = input.readUnsignedShort();
        header.nChannels = input.readUnsignedShort();
        header.nSampleRate = input.readUnsignedInt();
        header.nHeaderBytes = input.readUnsignedInt();
        header.nTerminatingBytes = input.readUnsignedInt();
        header.nTotalFrames = input.readUnsignedInt();
        header.nFinalFrameBlocks = input.readUnsignedInt();
        return header;
    }
}