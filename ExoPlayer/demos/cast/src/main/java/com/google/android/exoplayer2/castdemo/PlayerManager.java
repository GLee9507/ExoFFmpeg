/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.castdemo;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.view.KeyEvent;
import android.view.View;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.castdemo.DemoUtil.Sample;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastContext;
import java.util.ArrayList;

/** Manages players and an internal media queue for the ExoPlayer/Cast demo app. */
/* package */ final class PlayerManager
    implements EventListener, CastPlayer.SessionAvailabilityListener {

  /**
   * Listener for changes in the media queue playback position.
   */
  public interface QueuePositionListener {

    /**
     * Called when the currently played item of the media queue changes.
     */
    void onQueuePositionChanged(int previousIndex, int newIndex);

  }

  private static final String USER_AGENT = "ExoCastDemoPlayer";
  private static final DefaultHttpDataSourceFactory DATA_SOURCE_FACTORY =
      new DefaultHttpDataSourceFactory(USER_AGENT);

  private final PlayerView localPlayerView;
  private final PlayerControlView castControlView;
  private final SimpleExoPlayer exoPlayer;
  private final CastPlayer castPlayer;
  private final ArrayList<DemoUtil.Sample> mediaQueue;
  private final QueuePositionListener queuePositionListener;
  private final ConcatenatingMediaSource concatenatingMediaSource;

  private boolean castMediaQueueCreationPending;
  private int currentItemIndex;
  private Player currentPlayer;

  /**
   * @param queuePositionListener A {@link QueuePositionListener} for queue position changes.
   * @param localPlayerView The {@link PlayerView} for local playback.
   * @param castControlView The {@link PlayerControlView} to control remote playback.
   * @param context A {@link Context}.
   * @param castContext The {@link CastContext}.
   */
  public static PlayerManager createPlayerManager(
      QueuePositionListener queuePositionListener,
      PlayerView localPlayerView,
      PlayerControlView castControlView,
      Context context,
      CastContext castContext) {
    PlayerManager playerManager =
        new PlayerManager(
            queuePositionListener, localPlayerView, castControlView, context, castContext);
    playerManager.init();
    return playerManager;
  }

  private PlayerManager(
      QueuePositionListener queuePositionListener,
      PlayerView localPlayerView,
      PlayerControlView castControlView,
      Context context,
      CastContext castContext) {
    this.queuePositionListener = queuePositionListener;
    this.localPlayerView = localPlayerView;
    this.castControlView = castControlView;
    mediaQueue = new ArrayList<>();
    currentItemIndex = C.INDEX_UNSET;
    concatenatingMediaSource = new ConcatenatingMediaSource();

    DefaultTrackSelector trackSelector = new DefaultTrackSelector();
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    exoPlayer = ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector);
    exoPlayer.addListener(this);
    localPlayerView.setPlayer(exoPlayer);

    castPlayer = new CastPlayer(castContext);
    castPlayer.addListener(this);
    castPlayer.setSessionAvailabilityListener(this);
    castControlView.setPlayer(castPlayer);
  }

  // Queue manipulation methods.

  /**
   * Plays a specified queue item in the current player.
   *
   * @param itemIndex The index of the item to play.
   */
  public void selectQueueItem(int itemIndex) {
    setCurrentItem(itemIndex, C.TIME_UNSET, true);
  }

  /**
   * Returns the index of the currently played item.
   */
  public int getCurrentItemIndex() {
    return currentItemIndex;
  }

  /**
   * Appends {@code sample} to the media queue.
   *
   * @param sample The {@link Sample} to append.
   */
  public void addItem(Sample sample) {
    mediaQueue.add(sample);
    concatenatingMediaSource.addMediaSource(buildMediaSource(sample));
    if (currentPlayer == castPlayer) {
      castPlayer.addItems(buildMediaQueueItem(sample));
    }
  }

  /**
   * Returns the size of the media queue.
   */
  public int getMediaQueueSize() {
    return mediaQueue.size();
  }

  /**
   * Returns the item at the given index in the media queue.
   *
   * @param position The index of the item.
   * @return The item at the given index in the media queue.
   */
  public Sample getItem(int position) {
    return mediaQueue.get(position);
  }

  /**
   * Removes the item at the given index from the media queue.
   *
   * @param itemIndex The index of the item to remove.
   * @return Whether the removal was successful.
   */
  public boolean removeItem(int itemIndex) {
    concatenatingMediaSource.removeMediaSource(itemIndex);
    if (currentPlayer == castPlayer) {
      if (castPlayer.getPlaybackState() != Player.STATE_IDLE) {
        Timeline castTimeline = castPlayer.getCurrentTimeline();
        if (castTimeline.getPeriodCount() <= itemIndex) {
          return false;
        }
        castPlayer.removeItem((int) castTimeline.getPeriod(itemIndex, new Period()).id);
      }
    }
    mediaQueue.remove(itemIndex);
    if (itemIndex == currentItemIndex && itemIndex == mediaQueue.size()) {
      maybeSetCurrentItemAndNotify(C.INDEX_UNSET);
    } else if (itemIndex < currentItemIndex) {
      maybeSetCurrentItemAndNotify(currentItemIndex - 1);
    }
    return true;
  }

  /**
   * Moves an item within the queue.
   *
   * @param fromIndex The index of the item to move.
   * @param toIndex The target index of the item in the queue.
   * @return Whether the item move was successful.
   */
  public boolean moveItem(int fromIndex, int toIndex) {
    // Player update.
    concatenatingMediaSource.moveMediaSource(fromIndex, toIndex);
    if (currentPlayer == castPlayer && castPlayer.getPlaybackState() != Player.STATE_IDLE) {
      Timeline castTimeline = castPlayer.getCurrentTimeline();
      int periodCount = castTimeline.getPeriodCount();
      if (periodCount <= fromIndex || periodCount <= toIndex) {
        return false;
      }
      int elementId = (int) castTimeline.getPeriod(fromIndex, new Period()).id;
      castPlayer.moveItem(elementId, toIndex);
    }

    mediaQueue.add(toIndex, mediaQueue.remove(fromIndex));

    // Index update.
    if (fromIndex == currentItemIndex) {
      maybeSetCurrentItemAndNotify(toIndex);
    } else if (fromIndex < currentItemIndex && toIndex >= currentItemIndex) {
      maybeSetCurrentItemAndNotify(currentItemIndex - 1);
    } else if (fromIndex > currentItemIndex && toIndex <= currentItemIndex) {
      maybeSetCurrentItemAndNotify(currentItemIndex + 1);
    }

    return true;
  }

  // Miscellaneous methods.

  /**
   * Dispatches a given {@link KeyEvent} to the corresponding view of the current player.
   *
   * @param event The {@link KeyEvent}.
   * @return Whether the event was handled by the target view.
   */
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (currentPlayer == exoPlayer) {
      return localPlayerView.dispatchKeyEvent(event);
    } else /* currentPlayer == castPlayer */ {
      return castControlView.dispatchKeyEvent(event);
    }
  }

  /**
   * Releases the manager and the players that it holds.
   */
  public void release() {
    currentItemIndex = C.INDEX_UNSET;
    mediaQueue.clear();
    concatenatingMediaSource.clear();
    castPlayer.setSessionAvailabilityListener(null);
    castPlayer.release();
    localPlayerView.setPlayer(null);
    exoPlayer.release();
  }

  // Player.EventListener implementation.

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    updateCurrentItemIndex();
  }

  @Override
  public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
    updateCurrentItemIndex();
  }

  @Override
  public void onTimelineChanged(
      Timeline timeline, @Nullable Object manifest, @TimelineChangeReason int reason) {
    updateCurrentItemIndex();
    if (timeline.isEmpty()) {
      castMediaQueueCreationPending = true;
    }
  }

  // CastPlayer.SessionAvailabilityListener implementation.

  @Override
  public void onCastSessionAvailable() {
    setCurrentPlayer(castPlayer);
  }

  @Override
  public void onCastSessionUnavailable() {
    setCurrentPlayer(exoPlayer);
  }

  // Internal methods.

  private void init() {
    setCurrentPlayer(castPlayer.isCastSessionAvailable() ? castPlayer : exoPlayer);
  }

  private void updateCurrentItemIndex() {
    int playbackState = currentPlayer.getPlaybackState();
    maybeSetCurrentItemAndNotify(
        playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
        ? currentPlayer.getCurrentWindowIndex() : C.INDEX_UNSET);
  }

  private void setCurrentPlayer(Player currentPlayer) {
    if (this.currentPlayer == currentPlayer) {
      return;
    }

    // View management.
    if (currentPlayer == exoPlayer) {
      localPlayerView.setVisibility(View.VISIBLE);
      castControlView.hide();
    } else /* currentPlayer == castPlayer */ {
      localPlayerView.setVisibility(View.GONE);
      castControlView.show();
    }

    // Player state management.
    long playbackPositionMs = C.TIME_UNSET;
    int windowIndex = C.INDEX_UNSET;
    boolean playWhenReady = false;
    if (this.currentPlayer != null) {
      int playbackState = this.currentPlayer.getPlaybackState();
      if (playbackState != Player.STATE_ENDED) {
        playbackPositionMs = this.currentPlayer.getCurrentPosition();
        playWhenReady = this.currentPlayer.getPlayWhenReady();
        windowIndex = this.currentPlayer.getCurrentWindowIndex();
        if (windowIndex != currentItemIndex) {
          playbackPositionMs = C.TIME_UNSET;
          windowIndex = currentItemIndex;
        }
      }
      this.currentPlayer.stop(true);
    } else {
      // This is the initial setup. No need to save any state.
    }

    this.currentPlayer = currentPlayer;

    // Media queue management.
    castMediaQueueCreationPending = currentPlayer == castPlayer;
    if (currentPlayer == exoPlayer) {
      exoPlayer.prepare(concatenatingMediaSource);
    }

    // Playback transition.
    if (windowIndex != C.INDEX_UNSET) {
      setCurrentItem(windowIndex, playbackPositionMs, playWhenReady);
    }
  }

  /**
   * Starts playback of the item at the given position.
   *
   * @param itemIndex The index of the item to play.
   * @param positionMs The position at which playback should start.
   * @param playWhenReady Whether the player should proceed when ready to do so.
   */
  private void setCurrentItem(int itemIndex, long positionMs, boolean playWhenReady) {
    maybeSetCurrentItemAndNotify(itemIndex);
    if (castMediaQueueCreationPending) {
      MediaQueueItem[] items = new MediaQueueItem[mediaQueue.size()];
      for (int i = 0; i < items.length; i++) {
        items[i] = buildMediaQueueItem(mediaQueue.get(i));
      }
      castMediaQueueCreationPending = false;
      castPlayer.loadItems(items, itemIndex, positionMs, Player.REPEAT_MODE_OFF);
    } else {
      currentPlayer.seekTo(itemIndex, positionMs);
      currentPlayer.setPlayWhenReady(playWhenReady);
    }
  }

  private void maybeSetCurrentItemAndNotify(int currentItemIndex) {
    if (this.currentItemIndex != currentItemIndex) {
      int oldIndex = this.currentItemIndex;
      this.currentItemIndex = currentItemIndex;
      queuePositionListener.onQueuePositionChanged(oldIndex, currentItemIndex);
    }
  }

  private static MediaSource buildMediaSource(DemoUtil.Sample sample) {
    Uri uri = Uri.parse(sample.uri);
    switch (sample.mimeType) {
      case DemoUtil.MIME_TYPE_SS:
        return new SsMediaSource.Factory(DATA_SOURCE_FACTORY).createMediaSource(uri);
      case DemoUtil.MIME_TYPE_DASH:
        return new DashMediaSource.Factory(DATA_SOURCE_FACTORY).createMediaSource(uri);
      case DemoUtil.MIME_TYPE_HLS:
        return new HlsMediaSource.Factory(DATA_SOURCE_FACTORY).createMediaSource(uri);
      case DemoUtil.MIME_TYPE_VIDEO_MP4:
        return new ExtractorMediaSource.Factory(DATA_SOURCE_FACTORY).createMediaSource(uri);
      default: {
        throw new IllegalStateException("Unsupported type: " + sample.mimeType);
      }
    }
  }

  private static MediaQueueItem buildMediaQueueItem(DemoUtil.Sample sample) {
    MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
    movieMetadata.putString(MediaMetadata.KEY_TITLE, sample.name);
    MediaInfo mediaInfo = new MediaInfo.Builder(sample.uri)
        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(sample.mimeType)
        .setMetadata(movieMetadata).build();
    return new MediaQueueItem.Builder(mediaInfo).build();
  }

}
