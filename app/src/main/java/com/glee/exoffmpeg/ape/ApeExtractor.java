package com.glee.exoffmpeg.ape;

import com.glee.exoffmpeg.util.ExtractorInputWrapper;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;

import java.io.IOException;

/**
 * @author liji
 * @date 10/16/2018 3:26 PM
 * description
 */


public class ApeExtractor implements Extractor {
    private APEHeader header;

    @Override
    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return (header = APEHeader.sniff(new ExtractorInputWrapper(input))) != null;
    }

    @Override
    public void init(ExtractorOutput output) {
        try {
            APEFileInfo read = header.read();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public void seek(long position, long timeUs) {

    }

    @Override
    public void release() {

    }
}
