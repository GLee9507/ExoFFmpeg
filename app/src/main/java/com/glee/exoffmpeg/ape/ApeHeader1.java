package com.glee.exoffmpeg.ape;

import com.google.android.exoplayer2.extractor.ExtractorInput;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author liji
 * @date 10/16/2018 3:40 PM
 * description
 */


public class ApeHeader1 {
    private final static String APE_TAG = "MAC";
    private final ExtractorInput input;

    private ApeHeader1(ExtractorInput extractorInput) {
        this.input = extractorInput;
    }

    public static ApeHeader1 sniff(ExtractorInput input) throws IOException, InterruptedException {
        byte[] array = new byte[4];
        input.peekFully(array, 0, 4, true);
        return new String(array, Charset.forName("US-ASCII")).trim().equals(APE_TAG) ? new ApeHeader1(input) : null;
    }

    public void read() {

    }
}
