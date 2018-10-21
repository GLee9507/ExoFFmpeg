package com.glee.exoffmpeg.ape;


import com.glee.exoffmpeg.util.ExtractorInputWrapper;

import java.io.IOException;

/**
 * Author: Dmitry Vaguine
 * Date: 07.04.2004
 * Time: 14:36:53
 */
public class APEDescriptor {
    public String cID;                    // should equal 'MAC ' (char[4])
    public int nVersion;                // version number * 1000 (3.81 = 3810) (unsigned short)

    public long nDescriptorBytes;        // the number of descriptor bytes (allows later expansion of this header) (unsigned int32)
    public long nHeaderBytes;            // the number of header APE_HEADER bytes (unsigned int32)
    public long nSeekTableBytes;        // the number of bytes of the seek table (unsigned int32)
    public long nHeaderDataBytes;        // the number of header data bytes (from original file) (unsigned int32)
    public long nAPEFrameDataBytes;        // the number of bytes of APE frame data (unsigned int32)
    public long nAPEFrameDataBytesHigh;    // the high order number of APE frame data bytes (unsigned int32)
    public long nTerminatingDataBytes;    // the terminating data of the file (not including tag data) (unsigned int32)

    public byte[] cFileMD5 = new byte[16]; // the MD5 hash of the file (see notes for usage... it's a littly tricky) (unsigned char[16])

    public final static int APE_DESCRIPTOR_BYTES = 52;

    public static APEDescriptor read(final ExtractorInputWrapper input) throws IOException, InterruptedException {
        input.resetPeekPosition();
        APEDescriptor header = new APEDescriptor();
        header.cID = input.peekString(4, "US-ASCII");
        header.nVersion = input.peekUnsignedShort();
        input.advancePeekPosition(2);
        header.nDescriptorBytes = input.peekUnsignedInt();
        header.nHeaderBytes = input.peekUnsignedInt();
        header.nSeekTableBytes = input.peekUnsignedInt();
        header.nHeaderDataBytes = input.peekUnsignedInt();
        header.nAPEFrameDataBytes = input.peekUnsignedInt();
        header.nAPEFrameDataBytesHigh = input.peekUnsignedInt();
        header.nTerminatingDataBytes = input.peekUnsignedInt();
        input.peekFully(header.cFileMD5, 0, 16);
        return header;
    }

}