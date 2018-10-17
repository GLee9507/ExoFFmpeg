//package com.glee;
//
//import android.content.Context;
//import android.os.Handler;
//import android.support.annotation.Nullable;
//
//import com.google.android.exoplayer2.Renderer;
//import com.google.android.exoplayer2.RenderersFactory;
//import com.google.android.exoplayer2.audio.AudioRendererEventListener;
//import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
//import com.google.android.exoplayer2.drm.DrmSessionManager;
//import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
//import com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer;
//import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
//import com.google.android.exoplayer2.metadata.MetadataOutput;
//import com.google.android.exoplayer2.text.TextOutput;
//import com.google.android.exoplayer2.video.VideoRendererEventListener;
//
///**
// * @author liji
// * @date 10/17/2018 11:12 AM
// * description
// */
//
//
//public class AudioOnlyRenderersFactory implements RenderersFactory {
//    private final Context context;
//
//    public AudioOnlyRenderersFactory(Context context) {
//        this.context = context;
//    }
//
//    @Override
//    public Renderer[] createRenderers(Handler eventHandler,
//                                      VideoRendererEventListener videoRendererEventListener,
//                                      AudioRendererEventListener audioRendererEventListener,
//                                      TextOutput textRendererOutput,
//                                      MetadataOutput metadataRendererOutput,
//                                      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
//
//        return new Renderer[]{
//                new MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, eventHandler, audioRendererEventListener),
//                new LibflacAudioRenderer(eventHandler, audioRendererEventListener)
//        };
//    }
//}
