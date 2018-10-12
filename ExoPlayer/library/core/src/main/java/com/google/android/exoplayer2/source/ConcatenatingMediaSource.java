/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource.MediaSourceHolder;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concatenates multiple {@link MediaSource}s. The list of {@link MediaSource}s can be modified
 * during playback. It is valid for the same {@link MediaSource} instance to be present more than
 * once in the concatenation. Access to this class is thread-safe.
 */
public class ConcatenatingMediaSource extends CompositeMediaSource<MediaSourceHolder>
    implements PlayerMessage.Target {

  private static final int MSG_ADD = 0;
  private static final int MSG_REMOVE = 1;
  private static final int MSG_MOVE = 2;
  private static final int MSG_SET_SHUFFLE_ORDER = 3;
  private static final int MSG_NOTIFY_LISTENER = 4;
  private static final int MSG_ON_COMPLETION = 5;

  // Accessed on the app thread.
  private final List<MediaSourceHolder> mediaSourcesPublic;

  // Accessed on the playback thread.
  private final List<MediaSourceHolder> mediaSourceHolders;
  private final Map<MediaPeriod, MediaSourceHolder> mediaSourceByMediaPeriod;
  private final Map<Object, MediaSourceHolder> mediaSourceByUid;
  private final List<Runnable> pendingOnCompletionActions;
  private final boolean isAtomic;
  private final boolean useLazyPreparation;
  private final Timeline.Window window;

  private @Nullable ExoPlayer player;
  private @Nullable Handler playerApplicationHandler;
  private boolean listenerNotificationScheduled;
  private ShuffleOrder shuffleOrder;
  private int windowCount;
  private int periodCount;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this(/* isAtomic= */ false, mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(boolean isAtomic, MediaSource... mediaSources) {
    this(isAtomic, new DefaultShuffleOrder(0), mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(
      boolean isAtomic, ShuffleOrder shuffleOrder, MediaSource... mediaSources) {
    this(isAtomic, /* useLazyPreparation= */ false, shuffleOrder, mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param useLazyPreparation Whether playlist items are prepared lazily. If false, all manifest
   *     loads and other initial preparation steps happen immediately. If true, these initial
   *     preparations are triggered only when the player starts buffering the media.
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  @SuppressWarnings("initialization")
  public ConcatenatingMediaSource(
      boolean isAtomic,
      boolean useLazyPreparation,
      ShuffleOrder shuffleOrder,
      MediaSource... mediaSources) {
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
    }
    this.shuffleOrder = shuffleOrder.getLength() > 0 ? shuffleOrder.cloneAndClear() : shuffleOrder;
    this.mediaSourceByMediaPeriod = new IdentityHashMap<>();
    this.mediaSourceByUid = new HashMap<>();
    this.mediaSourcesPublic = new ArrayList<>();
    this.mediaSourceHolders = new ArrayList<>();
    this.pendingOnCompletionActions = new ArrayList<>();
    this.isAtomic = isAtomic;
    this.useLazyPreparation = useLazyPreparation;
    window = new Timeline.Window();
    addMediaSources(Arrays.asList(mediaSources));
  }

  /**
   * Appends a {@link MediaSource} to the playlist.
   *
   * @param mediaSource The {@link MediaSource} to be added to the list.
   */
  public final synchronized void addMediaSource(MediaSource mediaSource) {
    addMediaSource(mediaSourcesPublic.size(), mediaSource, null);
  }

  /**
   * Appends a {@link MediaSource} to the playlist and executes a custom action on completion.
   *
   * @param mediaSource The {@link MediaSource} to be added to the list.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source has been added to the playlist.
   */
  public final synchronized void addMediaSource(
      MediaSource mediaSource, @Nullable Runnable actionOnCompletion) {
    addMediaSource(mediaSourcesPublic.size(), mediaSource, actionOnCompletion);
  }

  /**
   * Adds a {@link MediaSource} to the playlist.
   *
   * @param index The index at which the new {@link MediaSource} will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSource The {@link MediaSource} to be added to the list.
   */
  public final synchronized void addMediaSource(int index, MediaSource mediaSource) {
    addMediaSource(index, mediaSource, null);
  }

  /**
   * Adds a {@link MediaSource} to the playlist and executes a custom action on completion.
   *
   * @param index The index at which the new {@link MediaSource} will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSource The {@link MediaSource} to be added to the list.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source has been added to the playlist.
   */
  public final synchronized void addMediaSource(
      int index, MediaSource mediaSource, @Nullable Runnable actionOnCompletion) {
    addMediaSources(index, Collections.singletonList(mediaSource), actionOnCompletion);
  }

  /**
   * Appends multiple {@link MediaSource}s to the playlist.
   *
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   */
  public final synchronized void addMediaSources(Collection<MediaSource> mediaSources) {
    addMediaSources(mediaSourcesPublic.size(), mediaSources, null);
  }

  /**
   * Appends multiple {@link MediaSource}s to the playlist and executes a custom action on
   * completion.
   *
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     sources have been added to the playlist.
   */
  public final synchronized void addMediaSources(
      Collection<MediaSource> mediaSources, @Nullable Runnable actionOnCompletion) {
    addMediaSources(mediaSourcesPublic.size(), mediaSources, actionOnCompletion);
  }

  /**
   * Adds multiple {@link MediaSource}s to the playlist.
   *
   * @param index The index at which the new {@link MediaSource}s will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   */
  public final synchronized void addMediaSources(int index, Collection<MediaSource> mediaSources) {
    addMediaSources(index, mediaSources, null);
  }

  /**
   * Adds multiple {@link MediaSource}s to the playlist and executes a custom action on completion.
   *
   * @param index The index at which the new {@link MediaSource}s will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     sources have been added to the playlist.
   */
  public final synchronized void addMediaSources(
      int index, Collection<MediaSource> mediaSources, @Nullable Runnable actionOnCompletion) {
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
    }
    List<MediaSourceHolder> mediaSourceHolders = new ArrayList<>(mediaSources.size());
    for (MediaSource mediaSource : mediaSources) {
      mediaSourceHolders.add(new MediaSourceHolder(mediaSource));
    }
    mediaSourcesPublic.addAll(index, mediaSourceHolders);
    if (player != null && !mediaSources.isEmpty()) {
      player
          .createMessage(this)
          .setType(MSG_ADD)
          .setPayload(new MessageData<>(index, mediaSourceHolders, actionOnCompletion))
          .send();
    } else if (actionOnCompletion != null) {
      actionOnCompletion.run();
    }
  }

  /**
   * Removes a {@link MediaSource} from the playlist.
   *
   * <p>Note: If you want to move the instance, it's preferable to use {@link #moveMediaSource(int,
   * int)} instead.
   *
   * <p>Note: If you want to remove a set of contiguous sources, it's preferable to use {@link
   * #removeMediaSourceRange(int, int)} instead.
   *
   * @param index The index at which the media source will be removed. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   */
  public final synchronized void removeMediaSource(int index) {
    removeMediaSource(index, null);
  }

  /**
   * Removes a {@link MediaSource} from the playlist and executes a custom action on completion.
   *
   * <p>Note: If you want to move the instance, it's preferable to use {@link #moveMediaSource(int,
   * int, Runnable)} instead.
   *
   * <p>Note: If you want to remove a set of contiguous sources, it's preferable to use {@link
   * #removeMediaSourceRange(int, int, Runnable)} instead.
   *
   * @param index The index at which the media source will be removed. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source has been removed from the playlist.
   */
  public final synchronized void removeMediaSource(
      int index, @Nullable Runnable actionOnCompletion) {
    removeMediaSourceRange(index, index + 1, actionOnCompletion);
  }

  /**
   * Removes a range of {@link MediaSource}s from the playlist, by specifying an initial index
   * (included) and a final index (excluded).
   *
   * <p>Note: when specified range is empty, no actual media source is removed and no exception is
   * thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source that will be
   *     removed. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @throws IndexOutOfBoundsException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &gt; {@link #getSize()}, {@code fromIndex} &gt; {@code toIndex}
   */
  public final synchronized void removeMediaSourceRange(int fromIndex, int toIndex) {
    removeMediaSourceRange(fromIndex, toIndex, null);
  }

  /**
   * Removes a range of {@link MediaSource}s from the playlist, by specifying an initial index
   * (included) and a final index (excluded), and executes a custom action on completion.
   *
   * <p>Note: when specified range is empty, no actual media source is removed and no exception is
   * thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source that will be
   *     removed. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source range has been removed from the playlist.
   * @throws IllegalArgumentException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &gt; {@link #getSize()}, {@code fromIndex} &gt; {@code toIndex}
   */
  public final synchronized void removeMediaSourceRange(
      int fromIndex, int toIndex, @Nullable Runnable actionOnCompletion) {
    Util.removeRange(mediaSourcesPublic, fromIndex, toIndex);
    if (fromIndex == toIndex) {
      if (actionOnCompletion != null) {
        actionOnCompletion.run();
      }
      return;
    }
    if (player != null) {
      player
          .createMessage(this)
          .setType(MSG_REMOVE)
          .setPayload(new MessageData<>(fromIndex, toIndex, actionOnCompletion))
          .send();
    } else if (actionOnCompletion != null) {
      actionOnCompletion.run();
    }
  }

  /**
   * Moves an existing {@link MediaSource} within the playlist.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   */
  public final synchronized void moveMediaSource(int currentIndex, int newIndex) {
    moveMediaSource(currentIndex, newIndex, null);
  }

  /**
   * Moves an existing {@link MediaSource} within the playlist and executes a custom action on
   * completion.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source has been moved.
   */
  public final synchronized void moveMediaSource(
      int currentIndex, int newIndex, @Nullable Runnable actionOnCompletion) {
    if (currentIndex == newIndex) {
      return;
    }
    mediaSourcesPublic.add(newIndex, mediaSourcesPublic.remove(currentIndex));
    if (player != null) {
      player
          .createMessage(this)
          .setType(MSG_MOVE)
          .setPayload(new MessageData<>(currentIndex, newIndex, actionOnCompletion))
          .send();
    } else if (actionOnCompletion != null) {
      actionOnCompletion.run();
    }
  }

  /** Clears the playlist. */
  public final synchronized void clear() {
    clear(/* actionOnCompletion= */ null);
  }

  /**
   * Clears the playlist and executes a custom action on completion.
   *
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the playlist
   *     has been cleared.
   */
  public final synchronized void clear(@Nullable Runnable actionOnCompletion) {
    removeMediaSourceRange(0, getSize(), actionOnCompletion);
  }

  /** Returns the number of media sources in the playlist. */
  public final synchronized int getSize() {
    return mediaSourcesPublic.size();
  }

  /**
   * Returns the {@link MediaSource} at a specified index.
   *
   * @param index An index in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @return The {@link MediaSource} at this index.
   */
  public final synchronized MediaSource getMediaSource(int index) {
    return mediaSourcesPublic.get(index).mediaSource;
  }

  /**
   * Sets a new shuffle order to use when shuffling the child media sources.
   *
   * @param shuffleOrder A {@link ShuffleOrder}.
   */
  public final synchronized void setShuffleOrder(ShuffleOrder shuffleOrder) {
    setShuffleOrder(shuffleOrder, /* actionOnCompletion= */ null);
  }

  /**
   * Sets a new shuffle order to use when shuffling the child media sources.
   *
   * @param shuffleOrder A {@link ShuffleOrder}.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the shuffle
   *     order has been changed.
   */
  public final synchronized void setShuffleOrder(
      ShuffleOrder shuffleOrder, @Nullable Runnable actionOnCompletion) {
    ExoPlayer player = this.player;
    if (player != null) {
      int size = getSize();
      if (shuffleOrder.getLength() != size) {
        shuffleOrder =
            shuffleOrder
                .cloneAndClear()
                .cloneAndInsert(/* insertionIndex= */ 0, /* insertionCount= */ size);
      }
      player
          .createMessage(this)
          .setType(MSG_SET_SHUFFLE_ORDER)
          .setPayload(new MessageData<>(/* index= */ 0, shuffleOrder, actionOnCompletion))
          .send();
    } else {
      this.shuffleOrder =
          shuffleOrder.getLength() > 0 ? shuffleOrder.cloneAndClear() : shuffleOrder;
      if (actionOnCompletion != null) {
        actionOnCompletion.run();
      }
    }
  }

  @Override
  public final synchronized void prepareSourceInternal(
      ExoPlayer player,
      boolean isTopLevelSource,
      @Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(player, isTopLevelSource, mediaTransferListener);
    this.player = player;
    playerApplicationHandler = new Handler(player.getApplicationLooper());
    if (mediaSourcesPublic.isEmpty()) {
      notifyListener();
    } else {
      shuffleOrder = shuffleOrder.cloneAndInsert(0, mediaSourcesPublic.size());
      addMediaSourcesInternal(0, mediaSourcesPublic);
      scheduleListenerNotification(/* actionOnCompletion= */ null);
    }
  }

  @Override
  @SuppressWarnings("MissingSuperCall")
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    // Do nothing. Source info refresh errors of the individual sources will be thrown when calling
    // DeferredMediaPeriod.maybeThrowPrepareError.
  }

  @Override
  public final MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    Object mediaSourceHolderUid = getMediaSourceHolderUid(id.periodUid);
    MediaSourceHolder holder = mediaSourceByUid.get(mediaSourceHolderUid);
    if (holder == null) {
      // Stale event. The media source has already been removed.
      holder = new MediaSourceHolder(new DummyMediaSource());
      holder.hasStartedPreparing = true;
    }
    DeferredMediaPeriod mediaPeriod = new DeferredMediaPeriod(holder.mediaSource, id, allocator);
    mediaSourceByMediaPeriod.put(mediaPeriod, holder);
    holder.activeMediaPeriods.add(mediaPeriod);
    if (!holder.hasStartedPreparing) {
      holder.hasStartedPreparing = true;
      prepareChildSource(holder, holder.mediaSource);
    } else if (holder.isPrepared) {
      MediaPeriodId idInSource = id.copyWithPeriodUid(getChildPeriodUid(holder, id.periodUid));
      mediaPeriod.createPeriod(idInSource);
    }
    return mediaPeriod;
  }

  @Override
  public final void releasePeriod(MediaPeriod mediaPeriod) {
    MediaSourceHolder holder =
        Assertions.checkNotNull(mediaSourceByMediaPeriod.remove(mediaPeriod));
    ((DeferredMediaPeriod) mediaPeriod).releasePeriod();
    holder.activeMediaPeriods.remove(mediaPeriod);
    if (holder.activeMediaPeriods.isEmpty() && holder.isRemoved) {
      releaseChildSource(holder);
    }
  }

  @Override
  public final void releaseSourceInternal() {
    super.releaseSourceInternal();
    mediaSourceHolders.clear();
    mediaSourceByUid.clear();
    player = null;
    playerApplicationHandler = null;
    shuffleOrder = shuffleOrder.cloneAndClear();
    windowCount = 0;
    periodCount = 0;
  }

  @Override
  protected final void onChildSourceInfoRefreshed(
      MediaSourceHolder mediaSourceHolder,
      MediaSource mediaSource,
      Timeline timeline,
      @Nullable Object manifest) {
    updateMediaSourceInternal(mediaSourceHolder, timeline);
  }

  @Override
  protected @Nullable MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      MediaSourceHolder mediaSourceHolder, MediaPeriodId mediaPeriodId) {
    for (int i = 0; i < mediaSourceHolder.activeMediaPeriods.size(); i++) {
      // Ensure the reported media period id has the same window sequence number as the one created
      // by this media source. Otherwise it does not belong to this child source.
      if (mediaSourceHolder.activeMediaPeriods.get(i).id.windowSequenceNumber
          == mediaPeriodId.windowSequenceNumber) {
        Object periodUid = getPeriodUid(mediaSourceHolder, mediaPeriodId.periodUid);
        return mediaPeriodId.copyWithPeriodUid(periodUid);
      }
    }
    return null;
  }

  @Override
  protected int getWindowIndexForChildWindowIndex(
      MediaSourceHolder mediaSourceHolder, int windowIndex) {
    return windowIndex + mediaSourceHolder.firstWindowIndexInChild;
  }

  @Override
  @SuppressWarnings("unchecked")
  public final void handleMessage(int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    if (player == null) {
      // Stale event.
      return;
    }
    switch (messageType) {
      case MSG_ADD:
        MessageData<Collection<MediaSourceHolder>> addMessage =
            (MessageData<Collection<MediaSourceHolder>>) Util.castNonNull(message);
        shuffleOrder = shuffleOrder.cloneAndInsert(addMessage.index, addMessage.customData.size());
        addMediaSourcesInternal(addMessage.index, addMessage.customData);
        scheduleListenerNotification(addMessage.actionOnCompletion);
        break;
      case MSG_REMOVE:
        MessageData<Integer> removeMessage = (MessageData<Integer>) Util.castNonNull(message);
        int fromIndex = removeMessage.index;
        int toIndex = removeMessage.customData;
        if (fromIndex == 0 && toIndex == shuffleOrder.getLength()) {
          shuffleOrder = shuffleOrder.cloneAndClear();
        } else {
          for (int index = toIndex - 1; index >= fromIndex; index--) {
            shuffleOrder = shuffleOrder.cloneAndRemove(index);
          }
        }
        for (int index = toIndex - 1; index >= fromIndex; index--) {
          removeMediaSourceInternal(index);
        }
        scheduleListenerNotification(removeMessage.actionOnCompletion);
        break;
      case MSG_MOVE:
        MessageData<Integer> moveMessage = (MessageData<Integer>) Util.castNonNull(message);
        shuffleOrder = shuffleOrder.cloneAndRemove(moveMessage.index);
        shuffleOrder = shuffleOrder.cloneAndInsert(moveMessage.customData, 1);
        moveMediaSourceInternal(moveMessage.index, moveMessage.customData);
        scheduleListenerNotification(moveMessage.actionOnCompletion);
        break;
      case MSG_SET_SHUFFLE_ORDER:
        MessageData<ShuffleOrder> shuffleOrderMessage =
            (MessageData<ShuffleOrder>) Util.castNonNull(message);
        shuffleOrder = shuffleOrderMessage.customData;
        scheduleListenerNotification(shuffleOrderMessage.actionOnCompletion);
        break;
      case MSG_NOTIFY_LISTENER:
        notifyListener();
        break;
      case MSG_ON_COMPLETION:
        List<Runnable> actionsOnCompletion = (List<Runnable>) Util.castNonNull(message);
        Handler handler = Assertions.checkNotNull(playerApplicationHandler);
        for (int i = 0; i < actionsOnCompletion.size(); i++) {
          handler.post(actionsOnCompletion.get(i));
        }
        break;
      default:
        throw new IllegalStateException();
    }
  }

  private void scheduleListenerNotification(@Nullable Runnable actionOnCompletion) {
    if (!listenerNotificationScheduled) {
      Assertions.checkNotNull(player).createMessage(this).setType(MSG_NOTIFY_LISTENER).send();
      listenerNotificationScheduled = true;
    }
    if (actionOnCompletion != null) {
      pendingOnCompletionActions.add(actionOnCompletion);
    }
  }

  private void notifyListener() {
    listenerNotificationScheduled = false;
    List<Runnable> actionsOnCompletion =
        pendingOnCompletionActions.isEmpty()
            ? Collections.emptyList()
            : new ArrayList<>(pendingOnCompletionActions);
    pendingOnCompletionActions.clear();
    refreshSourceInfo(
        new ConcatenatedTimeline(
            mediaSourceHolders, windowCount, periodCount, shuffleOrder, isAtomic),
        /* manifest= */ null);
    if (!actionsOnCompletion.isEmpty()) {
      Assertions.checkNotNull(player)
          .createMessage(this)
          .setType(MSG_ON_COMPLETION)
          .setPayload(actionsOnCompletion)
          .send();
    }
  }

  private void addMediaSourcesInternal(
      int index, Collection<MediaSourceHolder> mediaSourceHolders) {
    for (MediaSourceHolder mediaSourceHolder : mediaSourceHolders) {
      addMediaSourceInternal(index++, mediaSourceHolder);
    }
  }

  private void addMediaSourceInternal(int newIndex, MediaSourceHolder newMediaSourceHolder) {
    if (newIndex > 0) {
      MediaSourceHolder previousHolder = mediaSourceHolders.get(newIndex - 1);
      newMediaSourceHolder.reset(
          newIndex,
          previousHolder.firstWindowIndexInChild + previousHolder.timeline.getWindowCount(),
          previousHolder.firstPeriodIndexInChild + previousHolder.timeline.getPeriodCount());
    } else {
      newMediaSourceHolder.reset(
          newIndex, /* firstWindowIndexInChild= */ 0, /* firstPeriodIndexInChild= */ 0);
    }
    correctOffsets(
        newIndex,
        /* childIndexUpdate= */ 1,
        newMediaSourceHolder.timeline.getWindowCount(),
        newMediaSourceHolder.timeline.getPeriodCount());
    mediaSourceHolders.add(newIndex, newMediaSourceHolder);
    mediaSourceByUid.put(newMediaSourceHolder.uid, newMediaSourceHolder);
    if (!useLazyPreparation) {
      newMediaSourceHolder.hasStartedPreparing = true;
      prepareChildSource(newMediaSourceHolder, newMediaSourceHolder.mediaSource);
    }
  }

  private void updateMediaSourceInternal(MediaSourceHolder mediaSourceHolder, Timeline timeline) {
    if (mediaSourceHolder == null) {
      throw new IllegalArgumentException();
    }
    DeferredTimeline deferredTimeline = mediaSourceHolder.timeline;
    if (deferredTimeline.getTimeline() == timeline) {
      return;
    }
    int windowOffsetUpdate = timeline.getWindowCount() - deferredTimeline.getWindowCount();
    int periodOffsetUpdate = timeline.getPeriodCount() - deferredTimeline.getPeriodCount();
    if (windowOffsetUpdate != 0 || periodOffsetUpdate != 0) {
      correctOffsets(
          mediaSourceHolder.childIndex + 1,
          /* childIndexUpdate= */ 0,
          windowOffsetUpdate,
          periodOffsetUpdate);
    }
    mediaSourceHolder.timeline = deferredTimeline.cloneWithNewTimeline(timeline);
    if (!mediaSourceHolder.isPrepared && !timeline.isEmpty()) {
      timeline.getWindow(/* windowIndex= */ 0, window);
      long defaultPeriodPositionUs =
          window.getPositionInFirstPeriodUs() + window.getDefaultPositionUs();
      for (int i = 0; i < mediaSourceHolder.activeMediaPeriods.size(); i++) {
        DeferredMediaPeriod deferredMediaPeriod = mediaSourceHolder.activeMediaPeriods.get(i);
        deferredMediaPeriod.setDefaultPreparePositionUs(defaultPeriodPositionUs);
        MediaPeriodId idInSource =
            deferredMediaPeriod.id.copyWithPeriodUid(
                getChildPeriodUid(mediaSourceHolder, deferredMediaPeriod.id.periodUid));
        deferredMediaPeriod.createPeriod(idInSource);
      }
      mediaSourceHolder.isPrepared = true;
    }
    scheduleListenerNotification(/* actionOnCompletion= */ null);
  }

  private void removeMediaSourceInternal(int index) {
    MediaSourceHolder holder = mediaSourceHolders.remove(index);
    mediaSourceByUid.remove(holder.uid);
    Timeline oldTimeline = holder.timeline;
    correctOffsets(
        index,
        /* childIndexUpdate= */ -1,
        -oldTimeline.getWindowCount(),
        -oldTimeline.getPeriodCount());
    holder.isRemoved = true;
    if (holder.activeMediaPeriods.isEmpty()) {
      releaseChildSource(holder);
    }
  }

  private void moveMediaSourceInternal(int currentIndex, int newIndex) {
    int startIndex = Math.min(currentIndex, newIndex);
    int endIndex = Math.max(currentIndex, newIndex);
    int windowOffset = mediaSourceHolders.get(startIndex).firstWindowIndexInChild;
    int periodOffset = mediaSourceHolders.get(startIndex).firstPeriodIndexInChild;
    mediaSourceHolders.add(newIndex, mediaSourceHolders.remove(currentIndex));
    for (int i = startIndex; i <= endIndex; i++) {
      MediaSourceHolder holder = mediaSourceHolders.get(i);
      holder.firstWindowIndexInChild = windowOffset;
      holder.firstPeriodIndexInChild = periodOffset;
      windowOffset += holder.timeline.getWindowCount();
      periodOffset += holder.timeline.getPeriodCount();
    }
  }

  private void correctOffsets(
      int startIndex, int childIndexUpdate, int windowOffsetUpdate, int periodOffsetUpdate) {
    windowCount += windowOffsetUpdate;
    periodCount += periodOffsetUpdate;
    for (int i = startIndex; i < mediaSourceHolders.size(); i++) {
      mediaSourceHolders.get(i).childIndex += childIndexUpdate;
      mediaSourceHolders.get(i).firstWindowIndexInChild += windowOffsetUpdate;
      mediaSourceHolders.get(i).firstPeriodIndexInChild += periodOffsetUpdate;
    }
  }

  /** Return uid of media source holder from period uid of concatenated source. */
  private static Object getMediaSourceHolderUid(Object periodUid) {
    return ConcatenatedTimeline.getChildTimelineUidFromConcatenatedUid(periodUid);
  }

  /** Return uid of child period from period uid of concatenated source. */
  private static Object getChildPeriodUid(MediaSourceHolder holder, Object periodUid) {
    Object childUid = ConcatenatedTimeline.getChildPeriodUidFromConcatenatedUid(periodUid);
    return childUid.equals(DeferredTimeline.DUMMY_ID) ? holder.timeline.replacedId : childUid;
  }

  private static Object getPeriodUid(MediaSourceHolder holder, Object childPeriodUid) {
    if (holder.timeline.replacedId.equals(childPeriodUid)) {
      childPeriodUid = DeferredTimeline.DUMMY_ID;
    }
    return ConcatenatedTimeline.getConcatenatedUid(holder.uid, childPeriodUid);
  }

  /** Data class to hold playlist media sources together with meta data needed to process them. */
  /* package */ static final class MediaSourceHolder implements Comparable<MediaSourceHolder> {

    public final MediaSource mediaSource;
    public final Object uid;

    public DeferredTimeline timeline;
    public int childIndex;
    public int firstWindowIndexInChild;
    public int firstPeriodIndexInChild;
    public boolean hasStartedPreparing;
    public boolean isPrepared;
    public boolean isRemoved;
    public List<DeferredMediaPeriod> activeMediaPeriods;

    public MediaSourceHolder(MediaSource mediaSource) {
      this.mediaSource = mediaSource;
      this.timeline = new DeferredTimeline();
      this.activeMediaPeriods = new ArrayList<>();
      this.uid = new Object();
    }

    public void reset(int childIndex, int firstWindowIndexInChild, int firstPeriodIndexInChild) {
      this.childIndex = childIndex;
      this.firstWindowIndexInChild = firstWindowIndexInChild;
      this.firstPeriodIndexInChild = firstPeriodIndexInChild;
      this.hasStartedPreparing = false;
      this.isPrepared = false;
      this.isRemoved = false;
      this.activeMediaPeriods.clear();
    }

    @Override
    public int compareTo(@NonNull MediaSourceHolder other) {
      return this.firstPeriodIndexInChild - other.firstPeriodIndexInChild;
    }
  }

  /** Message used to post actions from app thread to playback thread. */
  private static final class MessageData<T> {

    public final int index;
    public final T customData;
    public final @Nullable Runnable actionOnCompletion;

    public MessageData(int index, T customData, @Nullable Runnable actionOnCompletion) {
      this.index = index;
      this.actionOnCompletion = actionOnCompletion;
      this.customData = customData;
    }
  }

  /** Timeline exposing concatenated timelines of playlist media sources. */
  private static final class ConcatenatedTimeline extends AbstractConcatenatedTimeline {

    private final int windowCount;
    private final int periodCount;
    private final int[] firstPeriodInChildIndices;
    private final int[] firstWindowInChildIndices;
    private final Timeline[] timelines;
    private final Object[] uids;
    private final HashMap<Object, Integer> childIndexByUid;

    public ConcatenatedTimeline(
        Collection<MediaSourceHolder> mediaSourceHolders,
        int windowCount,
        int periodCount,
        ShuffleOrder shuffleOrder,
        boolean isAtomic) {
      super(isAtomic, shuffleOrder);
      this.windowCount = windowCount;
      this.periodCount = periodCount;
      int childCount = mediaSourceHolders.size();
      firstPeriodInChildIndices = new int[childCount];
      firstWindowInChildIndices = new int[childCount];
      timelines = new Timeline[childCount];
      uids = new Object[childCount];
      childIndexByUid = new HashMap<>();
      int index = 0;
      for (MediaSourceHolder mediaSourceHolder : mediaSourceHolders) {
        timelines[index] = mediaSourceHolder.timeline;
        firstPeriodInChildIndices[index] = mediaSourceHolder.firstPeriodIndexInChild;
        firstWindowInChildIndices[index] = mediaSourceHolder.firstWindowIndexInChild;
        uids[index] = mediaSourceHolder.uid;
        childIndexByUid.put(uids[index], index++);
      }
    }

    @Override
    protected int getChildIndexByPeriodIndex(int periodIndex) {
      return Util.binarySearchFloor(firstPeriodInChildIndices, periodIndex + 1, false, false);
    }

    @Override
    protected int getChildIndexByWindowIndex(int windowIndex) {
      return Util.binarySearchFloor(firstWindowInChildIndices, windowIndex + 1, false, false);
    }

    @Override
    protected int getChildIndexByChildUid(Object childUid) {
      Integer index = childIndexByUid.get(childUid);
      return index == null ? C.INDEX_UNSET : index;
    }

    @Override
    protected Timeline getTimelineByChildIndex(int childIndex) {
      return timelines[childIndex];
    }

    @Override
    protected int getFirstPeriodIndexByChildIndex(int childIndex) {
      return firstPeriodInChildIndices[childIndex];
    }

    @Override
    protected int getFirstWindowIndexByChildIndex(int childIndex) {
      return firstWindowInChildIndices[childIndex];
    }

    @Override
    protected Object getChildUidByChildIndex(int childIndex) {
      return uids[childIndex];
    }

    @Override
    public int getWindowCount() {
      return windowCount;
    }

    @Override
    public int getPeriodCount() {
      return periodCount;
    }
  }

  /**
   * Timeline used as placeholder for an unprepared media source. After preparation, a copy of the
   * DeferredTimeline is used to keep the originally assigned first period ID.
   */
  private static final class DeferredTimeline extends ForwardingTimeline {

    private static final Object DUMMY_ID = new Object();
    private static final DummyTimeline dummyTimeline = new DummyTimeline();

    private final Object replacedId;

    public DeferredTimeline() {
      this(dummyTimeline, DUMMY_ID);
    }

    private DeferredTimeline(Timeline timeline, Object replacedId) {
      super(timeline);
      this.replacedId = replacedId;
    }

    public DeferredTimeline cloneWithNewTimeline(Timeline timeline) {
      return new DeferredTimeline(
          timeline,
          replacedId == DUMMY_ID && timeline.getPeriodCount() > 0
              ? timeline.getUidOfPeriod(0)
              : replacedId);
    }

    public Timeline getTimeline() {
      return timeline;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      timeline.getPeriod(periodIndex, period, setIds);
      if (Util.areEqual(period.uid, replacedId)) {
        period.uid = DUMMY_ID;
      }
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return timeline.getIndexOfPeriod(DUMMY_ID.equals(uid) ? replacedId : uid);
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      Object uid = timeline.getUidOfPeriod(periodIndex);
      return Util.areEqual(uid, replacedId) ? DUMMY_ID : uid;
    }
  }

  /** Dummy placeholder timeline with one dynamic window with a period of indeterminate duration. */
  private static final class DummyTimeline extends Timeline {

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(
        int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
      return window.set(
          /* tag= */ null,
          /* presentationStartTimeMs= */ C.TIME_UNSET,
          /* windowStartTimeMs= */ C.TIME_UNSET,
          /* isSeekable= */ false,
          // Dynamic window to indicate pending timeline updates.
          /* isDynamic= */ true,
          /* defaultPositionUs= */ 0,
          /* durationUs= */ C.TIME_UNSET,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ 0,
          /* positionInFirstPeriodUs= */ 0);
    }

    @Override
    public int getPeriodCount() {
      return 1;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      return period.set(
          /* id= */ 0,
          /* uid= */ DeferredTimeline.DUMMY_ID,
          /* windowIndex= */ 0,
          /* durationUs = */ C.TIME_UNSET,
          /* positionInWindowUs= */ 0);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return uid == DeferredTimeline.DUMMY_ID ? 0 : C.INDEX_UNSET;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      return DeferredTimeline.DUMMY_ID;
    }
  }

  /** Dummy media source which does nothing and does not support creating periods. */
  private static final class DummyMediaSource extends BaseMediaSource {

    @Override
    protected void prepareSourceInternal(
        ExoPlayer player,
        boolean isTopLevelSource,
        @Nullable TransferListener mediaTransferListener) {
      // Do nothing.
    }

    @Override
    protected void releaseSourceInternal() {
      // Do nothing.
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
      // Do nothing.
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
      // Do nothing.
    }
  }
}

