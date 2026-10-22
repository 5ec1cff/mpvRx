/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Apple Music editorial-video discovery inspired by BitChord:
 * https://github.com/kushagrasinghx/BitChord (GPL-3.0).
 * This implementation does not download or persist the artwork videos.
 */

package app.gyrolet.mpvrx.data.artwork

import android.util.Log
import app.gyrolet.mpvrx.network.awaitResponse
import java.io.IOException
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class MotionArtworkQuery(val title: String, val artist: String, val album: String?)

internal data class RemoteMotionArtwork(val urls: List<String>)

internal class AppleMusicArtworkRepository(client: OkHttpClient) {
  private val http = client.newBuilder()
    .cookieJar(CookieJar.NO_COOKIES)
    .callTimeout(20, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
  private val json = Json { ignoreUnknownKeys = true }
  private val mutex = Mutex()
  private data class Cached(val artwork: RemoteMotionArtwork?, val expiresAt: Long)
  private val cache = object : LinkedHashMap<MotionArtworkQuery, Cached>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MotionArtworkQuery, Cached>?): Boolean = size > 64
  }
  private var token: String? = null
  private var tokenExpiry = 0L
  private var nextTokenAttempt = 0L
  private val rejectedTokens = mutableSetOf<String>()

  suspend fun find(query: MotionArtworkQuery): RemoteMotionArtwork? = withContext(Dispatchers.IO) {
    mutex.withLock {
      val now = System.currentTimeMillis()
      cache[query]?.takeIf { it.expiresAt > now }?.let { return@withLock it.artwork }
      try {
        val artwork = search(query)
        cache[query] = Cached(artwork, now + if (artwork == null) 30 * MINUTE else 6 * HOUR)
        if (artwork == null) Log.d(TAG, "No matching animated album artwork available")
        artwork
      } catch (e: IOException) {
        Log.w(TAG, "Animated artwork lookup failed; retaining the existing cover", e)
        if (token == null) nextTokenAttempt = System.currentTimeMillis() + 5 * MINUTE
        cache[query] = Cached(null, now + 5 * MINUTE)
        null
      } catch (e: SerializationException) {
        Log.w(TAG, "Unexpected animated artwork response; retaining the existing cover", e)
        cache[query] = Cached(null, now + 5 * MINUTE)
        null
      }
    }
  }

  private suspend fun search(query: MotionArtworkQuery): RemoteMotionArtwork? {
    val storefront = Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z]{2}")) } ?: "us"
    val term = listOfNotNull(query.artist, query.title, query.album).joinToString(" ")
    val url = "$CATALOG/$storefront/search".toHttpUrl().newBuilder()
      .addQueryParameter("term", term)
      .addQueryParameter("types", "songs")
      .addQueryParameter("limit", "10")
      .addQueryParameter("include", "albums")
      .addQueryParameter("extend", "editorialVideo")
      .build().toString()
    val root = catalog(url) ?: return null
    val songs = root.obj("results")?.obj("songs")?.get("data") as? JsonArray ?: return null
    val visited = mutableSetOf<String>()
    for (element in songs) {
      val song = element as? JsonObject ?: continue
      val attributes = song.obj("attributes") ?: continue
      if (!matches(query, attributes)) continue
      motionArtwork(attributes)?.let { return it }
      val albums = song.obj("relationships")?.obj("albums")?.get("data") as? JsonArray ?: continue
      for (entry in albums) {
        val album = entry as? JsonObject ?: continue
        val id = album.text("id")?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() } ?: continue
        if (!visited.add(id)) continue
        if (visited.size > 3) return null
        val albumUrl = "$CATALOG/$storefront/albums/$id?extend=editorialVideo"
        val data = catalog(albumUrl)?.get("data") as? JsonArray ?: continue
        val albumAttributes = (data.firstOrNull() as? JsonObject)?.obj("attributes") ?: continue
        if (albumAttributes.text("name")?.let(::normalize) != attributes.text("albumName")?.let(::normalize)) continue
        if ((albumAttributes["isCompilation"] as? JsonPrimitive)?.booleanOrNull == true) continue
        motionArtwork(albumAttributes)?.let { return it }
      }
    }
    return null
  }

  private suspend fun catalog(url: String): JsonObject? {
    repeat(2) {
      val bearer = anonymousToken() ?: return null
      val request = Request.Builder().url(url)
        .header("Authorization", "Bearer $bearer")
        .header("Origin", "https://music.apple.com")
        .header("Referer", "https://music.apple.com/")
        .header("User-Agent", USER_AGENT)
        .build()
      try {
        return json.parseToJsonElement(get(request, 2 * 1024 * 1024)) as? JsonObject
      } catch (e: HttpFailure) {
        if (e.status != 401) throw e
        rejectedTokens.add(bearer)
        token = null
        nextTokenAttempt = 0L
        Log.w(TAG, "Anonymous artwork catalog authorization expired; refreshing")
      }
    }
    throw IOException("Anonymous artwork catalog authorization unavailable")
  }

  private suspend fun anonymousToken(): String? {
    val now = System.currentTimeMillis()
    token?.takeIf { tokenExpiry > now + MINUTE }?.let { return it }
    if (now < nextTokenAttempt) return null
    val html = get(Request.Builder().url("https://music.apple.com/us/browse").header("User-Agent", USER_AGENT).build(), 2 * 1024 * 1024)
    val scripts = SCRIPT.findAll(html).map { it.value }.distinct().take(3)
    for (path in scripts) {
      val script = get(Request.Builder().url("https://music.apple.com$path").header("User-Agent", USER_AGENT).build(), 8 * 1024 * 1024)
      for (match in JWT.findAll(script)) {
        val candidate = match.value
        if (candidate in rejectedTokens) continue
        val payload = tokenPayload(candidate) ?: continue
        if (payload.text("iss") != "AMPWebPlay") continue
        val expires = (payload["exp"] as? JsonPrimitive)?.longOrNull?.times(1000L) ?: continue
        if (expires <= now + MINUTE) continue
        token = candidate
        tokenExpiry = expires
        return candidate
      }
    }
    nextTokenAttempt = now + 30 * MINUTE
    Log.w(TAG, "Anonymous artwork catalog unavailable; backing off for 30 minutes")
    return null
  }

  private fun tokenPayload(value: String): JsonObject? =
    try {
      val payload = Base64.getUrlDecoder().decode(value.split('.')[1]).toString(Charsets.UTF_8)
      json.parseToJsonElement(payload) as? JsonObject
    } catch (e: SerializationException) {
      Log.d(TAG, "Ignoring malformed anonymous catalog authorization payload")
      null
    } catch (e: IllegalArgumentException) {
      Log.d(TAG, "Ignoring malformed anonymous catalog authorization")
      null
    }

  private suspend fun get(request: Request, maxBytes: Int): String =
    http.newCall(request).awaitResponse().use { response ->
      if (!response.isSuccessful) throw HttpFailure(response.code)
      val body = response.body ?: throw IOException("Empty artwork response")
      val source = body.source()
      source.request(maxBytes.toLong() + 1)
      if (source.buffer.size > maxBytes) throw IOException("Artwork response exceeds size limit")
      source.readUtf8()
    }

  private class HttpFailure(val status: Int) : IOException("Artwork provider HTTP $status")

  companion object {
    private const val TAG = "AppleMusicArtwork"
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val CATALOG = "https://amp-api.music.apple.com/v1/catalog"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"
    private val SCRIPT = Regex("""/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\.js""")
    private val JWT = Regex("""ey[A-Za-z0-9_-]+\.ey[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")

    private fun normalize(value: String): String =
      Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun matches(query: MotionArtworkQuery, attributes: JsonObject): Boolean {
      val title = attributes.text("name") ?: return false
      val artist = attributes.text("artistName") ?: return false
      val album = attributes.text("albumName") ?: return false
      return normalize(title) == normalize(query.title) &&
        normalize(artist) == normalize(query.artist) &&
        (query.album.isNullOrBlank() || normalize(album) == normalize(query.album))
    }

    private fun motionArtwork(attributes: JsonObject): RemoteMotionArtwork? {
      val video = attributes.obj("editorialVideo") ?: return null
      val urls = listOf("motionDetailSquare", "motionSquareVideo1x1", "motionDetailRaw", "motionDetailTall", "motionTallVideo3x4")
        .mapNotNull { key ->
          val asset = video.obj(key) ?: return@mapNotNull null
          sequenceOf("video", "videoUrl", "hlsUrl", "url").mapNotNull { asset.text(it) }
            .firstOrNull { value ->
              val url = value.toHttpUrlOrNull() ?: return@firstOrNull false
              url.isHttps && (url.host.endsWith(".apple.com") || url.host.endsWith(".mzstatic.com")) &&
                url.encodedPath.endsWith(".m3u8")
            }
        }.distinct()
      return urls.takeIf { it.isNotEmpty() }?.let(::RemoteMotionArtwork)
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
  }
}
