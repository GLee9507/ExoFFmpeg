package com.glee.exoffmpeg.ape;

/**
 * Author: Dmitry Vaguine
 * Date: 04.03.2004
 * Time: 14:51:31
 */
public class APEFileInfo {
    public int nVersion;                // file version number * 1000 (3.93 = 3930)
    public int nCompressionLevel;       // the compression level
    public int nFormatFlags;            // format flags
    public int nTotalFrames;            // the total number frames (frames are used internally)
    public int nBlocksPerFrame;         // the samples in a frame (frames are used internally)
    public int nFinalFrameBlocks;		// the number of samples in the final frame
    public int nChannels;				// audio channels
    public int nSampleRate;             // audio samples per second
    public int nBitsPerSample;          // audio bits per sample
    public int nBytesPerSample;         // audio bytes per sample
    public int nBlockAlign;             // audio block align (channels * bytes per sample)
    public int nWAVHeaderBytes;         // header bytes of the original WAV
    public int nWAVDataBytes;           // data bytes of the original WAV
    public int nWAVTerminatingBytes;    // terminating bytes of the original WAV
    public int nWAVTotalBytes;          // total bytes of the original WAV
    public int nAPETotalBytes;          // total bytes of the APE file
    public int nTotalBlocks;            // the total number audio blocks
    public int nLengthMS;               // the length in milliseconds
    public int nAverageBitrate;         // the kbps (i.e. 637 kpbs)
    public int nDecompressedBitrate;    // the kbps of the decompressed audio (i.e. 1440 kpbs for CD audio)
    public int nPeakLevel;				// the peak audio level (-1 if unknown)

    public int nJunkHeaderBytes;		// used for ID3v2, etc.
    public int nSeekTableElements;		// the number of elements in the seek table(s)

    public int[] spSeekByteTable;      // the seek table (byte)
    public byte[] spSeekBitTable;      // the seek table (bits -- legacy)
    public byte[] spWaveHeaderData;		// the pre-audio header data
    public APEDescriptor spAPEDescriptor;		// the descriptor (only with newer files)
}