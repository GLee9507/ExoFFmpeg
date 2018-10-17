package com.glee.exoffmpeg;

import com.glee.exoffmpeg.ape.ApeExtractor;
import com.google.android.exoplayer2.ext.flac.FlacExtractor;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.amr.AmrExtractor;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.ogg.OggExtractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.wav.WavExtractor;

/**
 * @author liji
 * @date 10/17/2018 8:07 PM
 * description
 */


public class AudioOnlyExtractorsFactory implements ExtractorsFactory {
    private boolean constantBitrateSeekingEnabled;
    private @AdtsExtractor.Flags
    int adtsFlags;
    private @AmrExtractor.Flags
    int amrFlags;
    private @Mp3Extractor.Flags
    int mp3Flags;

    public AudioOnlyExtractorsFactory() {
    }

    /**
     * Convenience method to set whether approximate seeking using constant bitrate assumptions should
     * be enabled for all extractors that support it. If set to true, the flags required to enable
     * this functionality will be OR'd with those passed to the setters when creating extractor
     * instances. If set to false then the flags passed to the setters will be used without
     * modification.
     *
     * @param constantBitrateSeekingEnabled Whether approximate seeking using a constant bitrate
     *                                      assumption should be enabled for all extractors that support it.
     * @return The factory, for convenience.
     */
    public synchronized AudioOnlyExtractorsFactory setConstantBitrateSeekingEnabled(
            boolean constantBitrateSeekingEnabled) {
        this.constantBitrateSeekingEnabled = constantBitrateSeekingEnabled;
        return this;
    }

    /**
     * Sets flags for {@link AdtsExtractor} instances created by the factory.
     *
     * @param flags The flags to use.
     * @return The factory, for convenience.
     * @see AdtsExtractor#AdtsExtractor(long, int)
     */
    public synchronized AudioOnlyExtractorsFactory setAdtsExtractorFlags(
            @AdtsExtractor.Flags int flags) {
        this.adtsFlags = flags;
        return this;
    }

    /**
     * Sets flags for {@link AmrExtractor} instances created by the factory.
     *
     * @param flags The flags to use.
     * @return The factory, for convenience.
     * @see AmrExtractor#AmrExtractor(int)
     */
    public synchronized AudioOnlyExtractorsFactory setAmrExtractorFlags(@AmrExtractor.Flags int flags) {
        this.amrFlags = flags;
        return this;
    }


    /**
     * Sets flags for {@link Mp3Extractor} instances created by the factory.
     *
     * @param flags The flags to use.
     * @return The factory, for convenience.
     * @see Mp3Extractor#Mp3Extractor(int)
     */
    public synchronized AudioOnlyExtractorsFactory setMp3ExtractorFlags(@Mp3Extractor.Flags int flags) {
        mp3Flags = flags;
        return this;
    }


    @Override
    public Extractor[] createExtractors() {
        return new Extractor[]{
                new Mp3Extractor(
                        mp3Flags
                                | (constantBitrateSeekingEnabled
                                ? Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                                : 0)),
                new FlacExtractor(),
                new AdtsExtractor(
                        /* firstStreamSampleTimestampUs= */ 0,
                        adtsFlags
                                | (constantBitrateSeekingEnabled
                                ? AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                                : 0)),
                new Ac3Extractor(),
                new OggExtractor(),
                new WavExtractor(),
                new AmrExtractor(
                        amrFlags
                                | (constantBitrateSeekingEnabled
                                ? AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                                : 0)),
                new ApeExtractor()
        };
    }
}
