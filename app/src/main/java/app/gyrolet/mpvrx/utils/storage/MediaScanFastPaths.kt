/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.utils.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.attribute.BasicFileAttributes

/*
 * Per-scan memoization helpers. Every MediaStore row and directory entry used to pay for its own
 * path normalization, .nomedia ancestry lookup, existence stat and canonicalization even though a
 * library has far fewer folders than files. These helpers do that work once per folder for the
 * lifetime of a single scan pass. They are not thread-safe; create one per scan.
 */

internal class ResolvedMediaFolder(
  val path: String,
  val key: String,
  val excluded: Boolean,
)

/** Resolves a raw MediaStore parent path to its normalized path, key and .nomedia state once. */
internal class MediaFolderResolver(
  private val noMediaPathFilter: NoMediaPathFilter,
) {
  private val resolved = HashMap<String, ResolvedMediaFolder?>()

  fun resolve(rawParent: String?): ResolvedMediaFolder? {
    if (rawParent == null) return null
    if (resolved.containsKey(rawParent)) return resolved[rawParent]

    val folder =
      normalizeStoragePath(rawParent)?.let { path ->
        storagePathKey(path)?.let { key ->
          ResolvedMediaFolder(
            path = path,
            key = key,
            excluded = noMediaPathFilter.shouldExcludeDirectory(File(rawParent)),
          )
        }
      }
    resolved[rawParent] = folder
    return folder
  }
}

/**
 * Answers File.exists() for MediaStore rows. Small folders keep per-file stats; once a folder has
 * been probed [listThreshold] times it is listed once and later rows are answered from that
 * listing. A listing miss still falls back to a real stat so case-folded or racing renames behave
 * exactly like File.exists().
 */
internal class FolderListingExistenceCache(
  private val listThreshold: Int = 4,
) {
  private val probeCounts = HashMap<String, Int>()
  private val listings = HashMap<String, Set<String>?>()

  fun exists(file: File): Boolean {
    val parent = file.parent ?: return file.exists()
    if (listings.containsKey(parent)) {
      val names = listings[parent]
      return (names != null && file.name in names) || file.exists()
    }

    val probes = (probeCounts[parent] ?: 0) + 1
    if (probes <= listThreshold) {
      probeCounts[parent] = probes
      return file.exists()
    }

    probeCounts.remove(parent)
    val names = File(parent).list()?.toHashSet()
    listings[parent] = names
    return (names != null && file.name in names) || file.exists()
  }
}

/**
 * Builds the same lookup key as [mediaPathKey] but resolves symlinks once per parent folder instead
 * of calling File.canonicalPath (one lstat per path component) for every media file.
 */
internal class MediaPathKeyResolver {
  private val canonicalParents = HashMap<String, String>()

  fun key(normalizedPath: String): String? {
    if (normalizedPath.startsWith("content://")) return mediaPathKey(normalizedPath)
    val separator = normalizedPath.lastIndexOf('/')
    if (separator <= 0 || separator == normalizedPath.lastIndex) return mediaPathKey(normalizedPath)

    val parent = normalizedPath.substring(0, separator)
    val canonicalParent =
      canonicalParents.getOrPut(parent) {
        runCatching { File(parent).canonicalPath }.getOrNull() ?: parent
      }
    return storagePathKey("$canonicalParent/${normalizedPath.substring(separator + 1)}")
  }
}

internal class FileEntryStat(
  val isDirectory: Boolean,
  val isFile: Boolean,
  val size: Long,
  val lastModifiedMs: Long,
)

/**
 * One stat for type, size and mtime. File.isDirectory/isFile/length/lastModified each issue their
 * own syscall, which is costly on FUSE-backed shared storage. Returns null where File would report
 * "neither file nor directory".
 */
internal fun readFileEntryStat(file: File): FileEntryStat? =
  try {
    val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
    FileEntryStat(
      isDirectory = attributes.isDirectory,
      isFile = attributes.isRegularFile,
      size = attributes.size(),
      lastModifiedMs = attributes.lastModifiedTime().toMillis(),
    )
  } catch (_: IOException) {
    null
  } catch (_: InvalidPathException) {
    null
  }
