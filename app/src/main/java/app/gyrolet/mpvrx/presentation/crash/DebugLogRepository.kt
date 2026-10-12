/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.presentation.crash

import android.os.Process
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal enum class DebugLogLevel(
  val code: String,
  val label: String,
) {
  Verbose("V", "Verbose"),
  Debug("D", "Debug"),
  Info("I", "Info"),
  Warn("W", "Warn"),
  Error("E", "Error"),
}

internal data class DebugLogEntry(
  val id: String,
  val timeMillis: Long,
  val timestamp: String,
  val level: DebugLogLevel,
  val tag: String,
  val message: String,
  val pid: Int? = null,
  val tid: Int? = null,
)

internal data class DebugLogSnapshot(
  val entries: List<DebugLogEntry>,
  val source: String,
  val rawLineCount: Int,
)

/**
 * Reads the current process' logcat in a device-tolerant way.
 *
 * Some vendor logcat binaries either do not support --pid or emit a slightly different
 * formatting even when a format is requested. The primary path uses Android's process filter;
 * if that produces no usable entries, a second pass reads a wider snapshot and filters the
 * parsed PID in-process. Unrecognised lines from the already PID-filtered primary command are
 * still surfaced instead of being silently discarded.
 */
internal object DebugLogReader {
  private const val MIN_RAW_LIMIT = 2_000
  private const val MAX_RAW_LIMIT = 100_000

  private val threadTimePattern =
    Regex(
      """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*:\s?(.*)$""",
    )
  private val classicTimePattern =
    Regex(
      """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^\(]+)\(\s*(\d+)\):\s?(.*)$""",
    )
  private val epochPattern =
    Regex(
      """^(\d+(?:\.\d+)?)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*:\s?(.*)$""",
    )
  private val briefPattern =
    Regex("""^([VDIWEF])/([^\(]+)\(\s*(\d+)\):\s?(.*)$""")

  fun readSnapshot(maxBytes: Int): DebugLogSnapshot {
    val pid = Process.myPid()
    val failures = mutableListOf<String>()
    val primaryRawLimit = (maxBytes / 128).coerceIn(MIN_RAW_LIMIT, MAX_RAW_LIMIT)
    val fallbackRawLimit = (primaryRawLimit * 2).coerceAtMost(MAX_RAW_LIMIT)

    val primary =
      runCommand(
        listOf(
          "logcat",
          "--pid=$pid",
          "-v",
          "threadtime",
          "-d",
          "-t",
          primaryRawLimit.toString(),
        ),
        maxBytes,
      )

    if (primary.exitCode == 0) {
      val parsed = parseLines(primary.lines, expectedPid = pid, allowRawFallback = true)
      if (parsed.isNotEmpty()) {
        return DebugLogSnapshot(
          entries = trimEntriesToByteBudget(parsed, maxBytes),
          source = "process-filtered",
          rawLineCount = primary.rawLineCount,
        )
      }
    } else {
      failures += primary.errorMessage("process-filtered logcat")
    }

    val fallback =
      runCommand(
        listOf(
          "logcat",
          "-v",
          "threadtime",
          "-d",
          "-t",
          fallbackRawLimit.toString(),
        ),
        maxBytes,
      )

    if (fallback.exitCode == 0) {
      val parsed = parseLines(fallback.lines, expectedPid = pid, allowRawFallback = false)
      if (parsed.isNotEmpty()) {
        return DebugLogSnapshot(
          entries = trimEntriesToByteBudget(parsed, maxBytes),
          source = "pid-fallback",
          rawLineCount = fallback.rawLineCount,
        )
      }

      // A successful command with no entries is not an error. Android can legitimately expose an
      // empty current-process buffer immediately after process start or after a buffer clear.
      return DebugLogSnapshot(
        entries = emptyList(),
        source = "empty",
        rawLineCount = fallback.rawLineCount,
      )
    }

    failures += fallback.errorMessage("fallback logcat")
    throw IOException(failures.filter { it.isNotBlank() }.joinToString("; ").ifBlank { "Unable to read logcat" })
  }

  private fun parseLines(
    lines: List<String>,
    expectedPid: Int?,
    allowRawFallback: Boolean,
  ): List<DebugLogEntry> {
    if (lines.isEmpty()) return emptyList()

    val now = System.currentTimeMillis()
    val parsed = ArrayList<DebugLogEntry>(lines.size)
    lines.forEachIndexed { index, line ->
      if (line.isBlank() || line.startsWith("--------- beginning of")) return@forEachIndexed

      val entry = parseKnownLine(line, now, index)
      if (entry != null) {
        if (expectedPid == null || entry.pid == null || entry.pid == expectedPid) {
          parsed += entry
        }
      } else if (allowRawFallback) {
        // The primary command is already --pid filtered. Preserve vendor-specific lines rather
        // than presenting an empty screen just because a vendor changed logcat formatting.
        parsed +=
          DebugLogEntry(
            id = buildEntryId(now, expectedPid, null, DebugLogLevel.Info, "logcat", line, index),
            timeMillis = now,
            timestamp = formatDisplayTime(now),
            level = DebugLogLevel.Info,
            tag = "logcat",
            message = line,
            pid = expectedPid,
          )
      }
    }
    return parsed
  }

  fun trimTextToByteBudget(text: String, maxBytes: Int): String {
    if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
    val kept = ArrayDeque<String>()
    var used = 0
    for (line in text.lineSequence().toList().asReversed()) {
      val bytes = line.toByteArray(Charsets.UTF_8).size + 1
      if (used + bytes > maxBytes) break
      kept.addFirst(line)
      used += bytes
    }
    return kept.joinToString("\n")
  }

  private fun trimEntriesToByteBudget(entries: List<DebugLogEntry>, maxBytes: Int): List<DebugLogEntry> {
    val kept = ArrayDeque<DebugLogEntry>()
    var used = 0
    for (entry in entries.asReversed()) {
      val bytes = buildString {
        append(entry.timestamp)
        entry.pid?.let { pid ->
          append(' ').append(pid)
          entry.tid?.let { tid -> append('/').append(tid) }
        }
        append(' ').append(entry.level.code).append('/').append(entry.tag).append(": ").append(entry.message).append('\n')
      }.toByteArray(Charsets.UTF_8).size
      if (used + bytes > maxBytes) break
      kept.addFirst(entry)
      used += bytes
    }
    return kept.toList()
  }

  private fun parseKnownLine(
    line: String,
    now: Long,
    occurrence: Int,
  ): DebugLogEntry? {
    threadTimePattern.matchEntire(line)?.let { match ->
      val timeMillis = parseAndroidTimestamp(match.groupValues[1], now) ?: now
      val pid = match.groupValues[2].toIntOrNull()
      val tid = match.groupValues[3].toIntOrNull()
      val level = match.groupValues[4].toDebugLogLevel() ?: return null
      val tag = match.groupValues[5].trim()
      val message = match.groupValues[6]
      return buildEntry(timeMillis, pid, tid, level, tag, message, occurrence)
    }

    classicTimePattern.matchEntire(line)?.let { match ->
      val timeMillis = parseAndroidTimestamp(match.groupValues[1], now) ?: now
      val level = match.groupValues[2].toDebugLogLevel() ?: return null
      val tag = match.groupValues[3].trim()
      val pid = match.groupValues[4].toIntOrNull()
      val message = match.groupValues[5]
      return buildEntry(timeMillis, pid, null, level, tag, message, occurrence)
    }

    epochPattern.matchEntire(line)?.let { match ->
      val timeMillis = (match.groupValues[1].toDoubleOrNull()?.times(1_000.0))?.toLong() ?: now
      val pid = match.groupValues[2].toIntOrNull()
      val tid = match.groupValues[3].toIntOrNull()
      val level = match.groupValues[4].toDebugLogLevel() ?: return null
      val tag = match.groupValues[5].trim()
      val message = match.groupValues[6]
      return buildEntry(timeMillis, pid, tid, level, tag, message, occurrence)
    }

    briefPattern.matchEntire(line)?.let { match ->
      val level = match.groupValues[1].toDebugLogLevel() ?: return null
      val tag = match.groupValues[2].trim()
      val pid = match.groupValues[3].toIntOrNull()
      val message = match.groupValues[4]
      return buildEntry(now, pid, null, level, tag, message, occurrence)
    }

    return null
  }

  private fun buildEntry(
    timeMillis: Long,
    pid: Int?,
    tid: Int?,
    level: DebugLogLevel,
    tag: String,
    message: String,
    occurrence: Int,
  ): DebugLogEntry =
    DebugLogEntry(
      id = buildEntryId(timeMillis, pid, tid, level, tag, message, occurrence),
      timeMillis = timeMillis,
      timestamp = formatDisplayTime(timeMillis),
      level = level,
      tag = tag,
      message = message,
      pid = pid,
      tid = tid,
    )

  private fun runCommand(
    command: List<String>,
    maxBytes: Int,
  ): CommandResult {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    return try {
      var rawLineCount = 0
      var retainedBytes = 0
      val lines = ArrayDeque<String>()
      BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
        while (true) {
          val line = reader.readLine() ?: break
          rawLineCount++
          val lineBytes = line.toByteArray(Charsets.UTF_8).size + 1
          lines.addLast(line)
          retainedBytes += lineBytes
          while (retainedBytes > maxBytes && lines.isNotEmpty()) {
            retainedBytes -= lines.removeFirst().toByteArray(Charsets.UTF_8).size + 1
          }
        }
      }
      CommandResult(process.waitFor(), lines.toList(), rawLineCount)
    } finally {
      process.destroy()
    }
  }

  private data class CommandResult(
    val exitCode: Int,
    val lines: List<String>,
    val rawLineCount: Int,
  ) {
    fun errorMessage(label: String): String {
      val detail = lines.takeLast(3).joinToString(" ").trim()
      return if (detail.isBlank()) "$label exited with code $exitCode" else "$label: $detail"
    }
  }
}

private fun String.toDebugLogLevel(): DebugLogLevel? =
  when (this) {
    "V" -> DebugLogLevel.Verbose
    "D" -> DebugLogLevel.Debug
    "I" -> DebugLogLevel.Info
    "W" -> DebugLogLevel.Warn
    "E", "F" -> DebugLogLevel.Error
    else -> null
  }

private fun buildEntryId(
  timeMillis: Long,
  pid: Int?,
  tid: Int?,
  level: DebugLogLevel,
  tag: String,
  message: String,
  occurrence: Int,
): String =
  buildString {
    append(timeMillis)
    append(':')
    append(pid ?: -1)
    append(':')
    append(tid ?: -1)
    append(':')
    append(level.code)
    append(':')
    append(tag.hashCode())
    append(':')
    append(message.hashCode())
    append(':')
    append(occurrence)
  }

private fun parseAndroidTimestamp(
  timestamp: String,
  now: Long,
): Long? =
  runCatching {
    val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    val parsed = formatter.parse(timestamp) ?: return@runCatching null
    val currentCalendar = Calendar.getInstance().apply { timeInMillis = now }
    val parsedCalendar =
      Calendar.getInstance().apply {
        time = parsed
        set(Calendar.YEAR, currentCalendar.get(Calendar.YEAR))
      }

    // A log buffer can cross New Year. If assigning the current year makes the log appear more
    // than a day in the future, it belongs to the previous year.
    if (parsedCalendar.timeInMillis > now + 24L * 60L * 60L * 1_000L) {
      parsedCalendar.add(Calendar.YEAR, -1)
    }
    parsedCalendar.timeInMillis
  }.getOrNull()

private fun formatDisplayTime(timeMillis: Long): String =
  SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timeMillis))
