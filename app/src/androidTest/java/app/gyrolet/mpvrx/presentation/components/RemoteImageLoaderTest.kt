/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.presentation.components

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gyrolet.mpvrx.utils.memory.BitmapMemoryCaches
import app.gyrolet.mpvrx.utils.memory.BitmapMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class RemoteImageLoaderTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test fun bulkMemoryTrimPublishesOnce() {
    val publications = AtomicInteger()
    val cache = object : BitmapMemoryCache<Int>(heapDivisor = 32, maxSizeMiB = 4) {
      override fun onMemoryTrim() { publications.incrementAndGet() }
    }
    val bitmaps = (1..8).map { Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888) }
    try {
      bitmaps.forEachIndexed { index, bitmap -> cache.put(index, bitmap) }
      @Suppress("DEPRECATION")
      BitmapMemoryCaches.trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
      assertEquals(0, cache.size())
      assertEquals(1, publications.get())
      assertTrue(bitmaps.none { it.isRecycled })
    } finally {
      cache.evictAll()
      bitmaps.forEach { it.recycle() }
    }
  }

  @Test fun smallArtworkDoesNotReplacePlayerResolution() = runBlocking {
    val requests = AtomicInteger()
    val client = client(png(2048)) { requests.incrementAndGet(); 200 }
    val url = url()
    val small = checkNotNull(RemoteImageLoader.load(context, client, url, maxDimensionPx = 128))
    val large = checkNotNull(RemoteImageLoader.load(context, client, url, maxDimensionPx = 1024))
    assertEquals(128, small.width)
    assertEquals(1024, large.width)
    assertEquals("Both tiers must share the disk original", 1, requests.get())
  }

  @Test fun memoryTrimmingDoesNotRecycleDisplayedArtworkOrDeleteDiskCopy() = runBlocking {
    val requests = AtomicInteger()
    val client = client(png(512)) { requests.incrementAndGet(); 200 }
    val url = url()
    val displayed = checkNotNull(RemoteImageLoader.load(context, client, url))
    @Suppress("DEPRECATION")
    BitmapMemoryCaches.trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    assertFalse(displayed.isRecycled)
    assertTrue(cacheFile(url).isFile)
    assertNotNull(RemoteImageLoader.load(context, client, url))
    assertEquals(1, requests.get())
  }

  @Test fun failedRefreshKeepsUsableStaleArtwork() = runBlocking {
    val bytes = png(512)
    val requests = AtomicInteger()
    val client = client(bytes) { if (requests.incrementAndGet() == 1) 200 else 503 }
    val url = url()
    assertNotNull(RemoteImageLoader.load(context, client, url))
    assertTrue(cacheFile(url).setLastModified(1L))
    @Suppress("DEPRECATION")
    BitmapMemoryCaches.trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    assertNotNull(RemoteImageLoader.load(context, client, url, cacheTtlMs = 1))
    assertEquals(2, requests.get())
    assertTrue(cacheFile(url).readBytes().contentEquals(bytes))
  }

  @Test fun concurrentImagesHaveBoundedTransfers() = runBlocking {
    val active = AtomicInteger()
    val peak = AtomicInteger()
    val bytes = png(256)
    val client = client(bytes) {
      val current = active.incrementAndGet()
      peak.updateAndGet { maxOf(it, current) }
      try { Thread.sleep(80) } finally { active.decrementAndGet() }
      200
    }
    val results = (1..12).map {
      async(Dispatchers.Default) { RemoteImageLoader.load(context, client, url(), maxDimensionPx = 128) }
    }.awaitAll()
    assertTrue(results.all { it != null })
    assertTrue("At most three different originals may transfer at once", peak.get() in 1..3)
  }

  @Test fun cancellingOneWaiterDoesNotCancelSharedArtwork() = runBlocking {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val requests = AtomicInteger()
    val client = client(png(256)) {
      requests.incrementAndGet()
      started.countDown()
      check(release.await(5, TimeUnit.SECONDS))
      200
    }
    val url = url()
    val first = async(Dispatchers.Default) { RemoteImageLoader.load(context, client, url) }
    try {
      assertTrue(started.await(5, TimeUnit.SECONDS))
      val second = async(Dispatchers.Default) { RemoteImageLoader.load(context, client, url) }
      first.cancel()
      release.countDown()
      assertNotNull(second.await())
      assertEquals(1, requests.get())
    } finally {
      release.countDown()
      first.cancel()
    }
  }

  private fun client(bytes: ByteArray, status: () -> Int): OkHttpClient =
    OkHttpClient.Builder().addInterceptor { chain ->
      Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(status())
        .message("fixture")
        .body(bytes.toResponseBody())
        .build()
    }.build()

  private fun url() = "https://mpvrx.test/${UUID.randomUUID()}.png"

  private fun png(size: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    return try {
      bitmap.eraseColor(0xff7965af.toInt())
      ByteArrayOutputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        output.toByteArray()
      }
    } finally {
      bitmap.recycle()
    }
  }

  private fun cacheFile(url: String): File {
    val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
      .joinToString("") { "%02x".format(it) }
    return File(context.cacheDir, "remote_images/$hash")
  }
}
