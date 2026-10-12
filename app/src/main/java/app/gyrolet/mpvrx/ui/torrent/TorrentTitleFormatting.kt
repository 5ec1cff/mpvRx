/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.torrent

import java.net.URI

private val seasonEpisodeRegex = Regex("(?i)\\bS\\d{1,2}[\\s.:_-]*E\\d{1,4}\\b")
private val crossFormatRegex = Regex("(?i)\\b\\d{1,2}x\\d{1,4}\\b")
private val episodeWordRegex = Regex("(?i)\\bep(?:isode)?[\\s.:_-]*\\d{1,4}\\b")
private val seasonRegex = Regex("(?i)\\bS(?:eason)?[\\s.:_-]*\\d{1,2}\\b")
private val knownExtensionRegex = Regex("(?i)\\.(?:torrent|mkv|mp4|m4v|webm|avi|mov|ts|m2ts|mp3|m4a|flac|ogg)$")
private val releaseNoiseRegex =
  Regex(
    "(?i)\\b(?:2160p|1080p|720p|480p|uhd|hdr10?|dv|dolby[ ._-]*vision|bluray|brrip|" +
      "web[ ._-]*dl|webrip|hdtv|x26[45]|hevc|av1|aac|dts|atmos|proper|repack)\\b.*$",
  )

internal fun prettyTorrentTitle(value: String): String =
  value
    .substringAfterLast('/')
    .replace(knownExtensionRegex, "")
    .replace(seasonEpisodeRegex, " ")
    .replace(crossFormatRegex, " ")
    .replace(episodeWordRegex, " ")
    .replace(seasonRegex, " ")
    .replace(releaseNoiseRegex, " ")
    .replace(Regex("[\\[\\]【】()（）]"), " ")
    .replace(Regex("[._]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim(' ', '-', '_', ':', '.')
    .ifBlank { "Torrent" }

internal fun cleanSearchTitle(value: String): String =
  prettyTorrentTitle(value)
    .replace(Regex("\\s+"), " ")
    .trim()

internal fun tmdbImageUrl(
  path: String?,
  size: String,
): String? {
  val value = path?.trim()?.takeIf(String::isNotBlank) ?: return null
  return when {
    safeRemoteImageUrl(value) != null -> safeRemoteImageUrl(value)
    value.startsWith('/') -> "https://image.tmdb.org/t/p/$size$value"
    else -> "https://image.tmdb.org/t/p/$size/$value"
  }
}

internal fun safeRemoteImageUrl(value: String?): String? {
  val candidate = value?.trim()?.takeIf(String::isNotBlank) ?: return null
  return runCatching {
    val uri = URI(candidate)
    candidate.takeIf {
      uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        !uri.host.equals("localhost", ignoreCase = true) &&
        uri.host != "127.0.0.1" &&
        uri.host != "::1"
    }
  }.getOrNull()
}
