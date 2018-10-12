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
package com.google.android.exoplayer2.upstream.cache;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SimpleCacheSpan}. */
@RunWith(AndroidJUnit4.class)
public class SimpleCacheSpanTest {

  private CachedContentIndex index;
  private File cacheDir;

  public static File createCacheSpanFile(File cacheDir, int id, long offset, int length,
      long lastAccessTimestamp) throws IOException {
    File cacheFile = SimpleCacheSpan.getCacheFile(cacheDir, id, offset, lastAccessTimestamp);
    createTestFile(cacheFile, length);
    return cacheFile;
  }

  public static CacheSpan createCacheSpan(CachedContentIndex index, File cacheDir, String key,
      long offset, int length, long lastAccessTimestamp) throws IOException {
    int id = index.assignIdForKey(key);
    File cacheFile = createCacheSpanFile(cacheDir, id, offset, length, lastAccessTimestamp);
    return SimpleCacheSpan.createCacheEntry(cacheFile, index);
  }

  @Before
  public void setUp() throws Exception {
    cacheDir =
        Util.createTempDirectory(InstrumentationRegistry.getTargetContext(), "ExoPlayerTest");
    index = new CachedContentIndex(cacheDir);
  }

  @After
  public void tearDown() {
    Util.recursiveDelete(cacheDir);
  }

  @Test
  public void testCacheFile() throws Exception {
    assertCacheSpan("key1", 0, 0);
    assertCacheSpan("key2", 1, 2);
    assertCacheSpan("<>:\"/\\|?*%", 1, 2);
    assertCacheSpan("key3", 1, 2);

    assertNullCacheSpan(new File("parent"), "key4", -1, 2);
    assertNullCacheSpan(new File("parent"), "key5", 1, -2);

    assertCacheSpan(
        "A newline (line feed) character \n"
            + "A carriage-return character followed immediately by a newline character \r\n"
            + "A standalone carriage-return character \r"
            + "A next-line character \u0085"
            + "A line-separator character \u2028"
            + "A paragraph-separator character \u2029", 1, 2);
  }

  @Test
  public void testUpgradeFileName() throws Exception {
    String key = "asd\u00aa";
    int id = index.assignIdForKey(key);
    File v3file = createTestFile(id + ".0.1.v3.exo");
    File v2file = createTestFile("asd%aa.1.2.v2.exo");
    File wrongEscapedV2file = createTestFile("asd%za.3.4.v2.exo");
    File v1File = createTestFile("asd\u00aa.5.6.v1.exo");

    for (File file : cacheDir.listFiles()) {
      SimpleCacheSpan cacheEntry = SimpleCacheSpan.createCacheEntry(file, index);
      if (file.equals(wrongEscapedV2file)) {
        assertThat(cacheEntry).isNull();
      } else {
        assertThat(cacheEntry).isNotNull();
      }
    }

    assertThat(v3file.exists()).isTrue();
    assertThat(v2file.exists()).isFalse();
    assertThat(wrongEscapedV2file.exists()).isTrue();
    assertThat(v1File.exists()).isFalse();

    File[] files = cacheDir.listFiles();
    assertThat(files).hasLength(4);

    Set<String> keys = index.getKeys();
    assertWithMessage("There should be only one key for all files.").that(keys).hasSize(1);
    assertThat(keys).contains(key);

    TreeSet<SimpleCacheSpan> spans = index.get(key).getSpans();
    assertWithMessage("upgradeOldFiles() shouldn't add any spans.").that(spans.isEmpty()).isTrue();

    HashMap<Long, Long> cachedPositions = new HashMap<>();
    for (File file : files) {
      SimpleCacheSpan cacheSpan = SimpleCacheSpan.createCacheEntry(file, index);
      if (cacheSpan != null) {
        assertThat(cacheSpan.key).isEqualTo(key);
        cachedPositions.put(cacheSpan.position, cacheSpan.lastAccessTimestamp);
      }
    }

    assertThat(cachedPositions.get((long) 0)).isEqualTo(1);
    assertThat(cachedPositions.get((long) 1)).isEqualTo(2);
    assertThat(cachedPositions.get((long) 5)).isEqualTo(6);
  }

  private static void createTestFile(File file, int length) throws IOException {
    FileOutputStream output = new FileOutputStream(file);
    for (int i = 0; i < length; i++) {
      output.write(i);
    }
    output.close();
  }

  private File createTestFile(String name) throws IOException {
    File file = new File(cacheDir, name);
    createTestFile(file, 1);
    return file;
  }

  private void assertCacheSpan(String key, long offset, long lastAccessTimestamp)
      throws IOException {
    int id = index.assignIdForKey(key);
    File cacheFile = createCacheSpanFile(cacheDir, id, offset, 1, lastAccessTimestamp);
    SimpleCacheSpan cacheSpan = SimpleCacheSpan.createCacheEntry(cacheFile, index);
    String message = cacheFile.toString();
    assertWithMessage(message).that(cacheSpan).isNotNull();
    assertWithMessage(message).that(cacheFile.getParentFile()).isEqualTo(cacheDir);
    assertWithMessage(message).that(cacheSpan.key).isEqualTo(key);
    assertWithMessage(message).that(cacheSpan.position).isEqualTo(offset);
    assertWithMessage(message).that(cacheSpan.length).isEqualTo(1);
    assertWithMessage(message).that(cacheSpan.isCached).isTrue();
    assertWithMessage(message).that(cacheSpan.file).isEqualTo(cacheFile);
    assertWithMessage(message).that(cacheSpan.lastAccessTimestamp).isEqualTo(lastAccessTimestamp);
  }

  private void assertNullCacheSpan(File parent, String key, long offset,
      long lastAccessTimestamp) {
    File cacheFile = SimpleCacheSpan.getCacheFile(parent, index.assignIdForKey(key), offset,
        lastAccessTimestamp);
    CacheSpan cacheSpan = SimpleCacheSpan.createCacheEntry(cacheFile, index);
    assertWithMessage(cacheFile.toString()).that(cacheSpan).isNull();
  }

}
