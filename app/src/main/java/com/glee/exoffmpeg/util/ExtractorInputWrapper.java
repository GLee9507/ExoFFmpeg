package com.glee.exoffmpeg.util;

import android.text.TextUtils;

import com.google.android.exoplayer2.extractor.ExtractorInput;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author liji
 * @date 10/16/2018 3:54 PM
 * description Extractor包装类，实现read
 */


public class ExtractorInputWrapper implements ExtractorInput {
    private final ExtractorInput input;
    private final byte[] bytes = new byte[8];

    public ExtractorInputWrapper(ExtractorInput input) {
        this.input = input;
    }

    public String peekString(int size, String encoding) throws IOException, InterruptedException {
        Charset c;
        if (TextUtils.isEmpty(encoding))
            c = Charset.defaultCharset();
        else
            c = Charset.forName(encoding);
        byte[] bytes = new byte[size];
        peekFully(bytes, 0, size);
        return new String(bytes, 0, size, c);
    }

    public short peekUnsignedByte() throws IOException, InterruptedException {
        peekFully(bytes, 0, 1);
        return (short) (bytes[0] & 0xff);
    }

    public int peekUnsignedShort() throws IOException, InterruptedException {
        peekFully(bytes, 0, 2);
        return (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8);
    }

    public long peekUnsignedInt() throws IOException, InterruptedException {
        peekFully(bytes, 0, 4);
        return ((long) (bytes[0] & 0xff)) |
                (((long) (bytes[1] & 0xff)) << 8) |
                (((long) (bytes[2] & 0xff)) << 16) |
                (((long) (bytes[3] & 0xff)) << 24);
    }

    public byte peekByte() throws IOException, InterruptedException {
        peekFully(bytes, 0, 1);
        return bytes[0];
    }

    public short peekShort() throws IOException, InterruptedException {
//        byte[] bytes = new byte[2];
        peekFully(bytes, 0, 2);
        return (short) ((bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8));
    }

    public int peekInt() throws IOException, InterruptedException {
//        byte[] bytes = new byte[4];
        peekFully(bytes, 0, 4);
        return (int) (((long) ((bytes)[0] & 0xff)) |
                (((long) (bytes[1] & 0xff)) << 8) |
                (((long) (bytes[2] & 0xff)) << 16) |
                (((long) (bytes[3] & 0xff)) << 24));
    }

    public long peekLong() throws IOException, InterruptedException {
        peekFully(bytes, 0, 8);
        return ((long) (bytes[0] & 0xff)) |
                (((long) (bytes[1] & 0xff)) << 8) |
                (((long) (bytes[2] & 0xff)) << 16) |
                (((long) (bytes[3] & 0xff)) << 24) |
                (((long) (bytes[4] & 0xff)) << 32) |
                (((long) (bytes[5] & 0xff)) << 40) |
                (((long) (bytes[6] & 0xff)) << 48) |
                (((long) (bytes[7] & 0xff)) << 56);
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException, InterruptedException {
        return input.read(target, offset, length);
    }

    @Override
    public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return input.readFully(target, offset, length, allowEndOfInput);
    }

    @Override
    public void readFully(byte[] target, int offset, int length) throws IOException, InterruptedException {
        input.readFully(target, offset, length);
    }

    @Override
    public int skip(int length) throws IOException, InterruptedException {
        return input.skip(length);
    }

    @Override
    public boolean skipFully(int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return input.skipFully(length, allowEndOfInput);
    }

    @Override
    public void skipFully(int length) throws IOException, InterruptedException {
        input.skipFully(length);
    }

    @Override
    public boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return input.peekFully(target, offset, length, allowEndOfInput);
    }

    @Override
    public void peekFully(byte[] target, int offset, int length) throws IOException, InterruptedException {
        input.peekFully(target, offset, length);
    }

    @Override
    public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return input.advancePeekPosition(length, allowEndOfInput);
    }

    @Override
    public void advancePeekPosition(int length) throws IOException, InterruptedException {
        input.advancePeekPosition(length);
    }

    @Override
    public void resetPeekPosition() {
        input.resetPeekPosition();
    }

    @Override
    public long getPeekPosition() {
        return input.getPeekPosition();
    }

    @Override
    public long getPosition() {
        return input.getPosition();
    }

    @Override
    public long getLength() {
        return input.getLength();
    }

    @Override
    public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
        input.setRetryPosition(position, e);
    }
}
