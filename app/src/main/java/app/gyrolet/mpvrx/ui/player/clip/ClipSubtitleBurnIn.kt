/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.gyrolet.mpvrx.ui.player.clip

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset

/** Text cues resolved before Transformer starts; export never silently drops requested subtitles. */
internal data class ClipSubtitleCue(
  val startUs: Long,
  val endUs: Long,
  val text: String,
)

internal object ClipSubtitleBurnIn {
  fun load(
    context: Context,
    source: String,
    track: ClipTrackOption,
    headers: Map<String, String>,
  ): List<ClipSubtitleCue> {
    require(track.type == "sub") { "A subtitle track is required" }
    val cues = track.externalFile?.let { external ->
      val data = readText(context, external)
      parseTextCues(data)
    } ?: loadEmbeddedText(context, source, headers, track.ordinal)
    check(cues.isNotEmpty()) {
      "The selected subtitle track cannot be burned. Text subtitles (SRT, VTT, ASS and supported embedded text) are supported; image-based subtitles need a different renderer."
    }
    return cues.sortedBy { it.startUs }
  }

  private fun readText(context: Context, source: String): String {
    val uri = Uri.parse(source)
    val bytes = when (uri.scheme?.lowercase()) {
      "content", "android.resource" ->
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      "file" -> uri.path?.let { File(it).readBytes() }
      null, "" -> File(source).readBytes()
      else -> null
    } ?: error("Cannot read the selected subtitle file")
    // UTF-8 BOM is common in manually supplied SRTs.
    return bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
  }

  private fun loadEmbeddedText(
    context: Context,
    source: String,
    headers: Map<String, String>,
    subtitleOrdinal: Int,
  ): List<ClipSubtitleCue> {
    val extractor = MediaExtractor()
    try {
      val uri = Uri.parse(source)
      if (uri.scheme.isNullOrEmpty() || uri.scheme.equals("file", true)) {
        extractor.setDataSource(uri.path ?: source)
      } else {
        extractor.setDataSource(context, uri, headers)
      }
      val textTrackIndexes = (0 until extractor.trackCount).filter { i ->
        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().lowercase()
        mime.contains("subrip") || mime.contains("vtt") || mime.contains("ssa") ||
          mime.contains("ass") || mime.contains("text")
      }
      val selectedIndex = textTrackIndexes.getOrNull(subtitleOrdinal)
        ?: error("The selected embedded subtitle format is not supported for burn-in on this device")
      extractor.selectTrack(selectedIndex)
      val buffer = ByteBuffer.allocate(1024 * 1024)
      val result = mutableListOf<ClipSubtitleCue>()
      while (result.size < 20_000) {
        buffer.clear()
        val length = extractor.readSampleData(buffer, 0)
        if (length < 0) break
        val timeUs = extractor.sampleTime
        if (timeUs < 0) break
        val bytes = ByteArray(length)
        buffer.position(0)
        buffer.get(bytes, 0, length)
        val decoded = bytes.toString(Charsets.UTF_8).trimEnd('\u0000')
        val parsed = parseTextCues(decoded)
        if (parsed.isNotEmpty()) {
          // Embedded samples may contain relative timing; sample timestamps anchor them.
          result += parsed.map { cue ->
            if (cue.startUs < 1_000_000L && timeUs > 1_000_000L) {
              cue.copy(startUs = cue.startUs + timeUs, endUs = cue.endUs + timeUs)
            } else cue
          }
        } else if (decoded.isNotBlank()) {
          val cleaned = cleanText(decoded)
          if (cleaned.isNotBlank()) result += ClipSubtitleCue(timeUs, timeUs + 3_000_000L, cleaned)
        }
        if (!extractor.advance()) break
      }
      return result
    } finally {
      extractor.release()
    }
  }

  private fun parseTextCues(input: String): List<ClipSubtitleCue> {
    val normalized = input.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isEmpty()) return emptyList()
    if (normalized.contains("[Events]", ignoreCase = true) ||
      normalized.lineSequence().any { it.startsWith("Dialogue:", ignoreCase = true) }
    ) {
      return normalized.lineSequence().mapNotNull { line ->
        if (!line.startsWith("Dialogue:", ignoreCase = true)) return@mapNotNull null
        val parts = line.substringAfter(':').trim().split(",", limit = 10)
        if (parts.size < 10) return@mapNotNull null
        val start = parseTimestamp(parts[1]) ?: return@mapNotNull null
        val end = parseTimestamp(parts[2]) ?: return@mapNotNull null
        val text = cleanText(parts[9].replace("\\N", "\n").replace("\\n", "\n"))
        if (end <= start || text.isBlank()) null else ClipSubtitleCue(start, end, text)
      }.toList()
    }

    return normalized.split(Regex("\n\\s*\n")).mapNotNull { section ->
      val lines = section.lines()
      val timeIndex = lines.indexOfFirst { "-->" in it }
      if (timeIndex < 0) return@mapNotNull null
      val interval = lines[timeIndex].split("-->", limit = 2)
      if (interval.size != 2) return@mapNotNull null
      val start = parseTimestamp(interval[0]) ?: return@mapNotNull null
      val end = parseTimestamp(interval[1].trim().substringBefore(' ')) ?: return@mapNotNull null
      val text = cleanText(lines.drop(timeIndex + 1).joinToString("\n"))
      if (end <= start || text.isBlank()) null else ClipSubtitleCue(start, end, text)
    }
  }

  private fun parseTimestamp(value: String): Long? = runCatching {
    val parts = value.trim().replace(',', '.').split(':')
    if (parts.size !in 2..3) return null
    val h = if (parts.size == 3) parts[0].toLong() else 0L
    val m = parts[parts.size - 2].toLong()
    val seconds = parts.last().toDouble()
    ((h * 3600 + m * 60) * 1_000_000L + seconds * 1_000_000.0).toLong()
  }.getOrNull()

  private fun cleanText(value: String): String =
    value.replace(Regex("\\{[^}]*}"), "")
      .replace(Regex("<[^>]+>"), "")
      .trim()
}
