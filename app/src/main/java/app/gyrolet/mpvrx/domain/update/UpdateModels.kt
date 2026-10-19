/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.domain.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class AppUpdateChannel {
  STABLE,
  PREVIEW,
  EXPERIMENTAL,
  ;

  companion object {
    fun fromStoredValue(value: String?): AppUpdateChannel =
      entries.firstOrNull { channel -> channel.name.equals(value, ignoreCase = true) } ?: STABLE
  }
}

@Serializable
data class Release(
  @SerialName("tag_name") val tagName: String,
  @SerialName("name") val name: String,
  @SerialName("body") val body: String,
  @SerialName("published_at") val publishedAt: String,
  @SerialName("assets") val assets: List<Asset>,
  @SerialName("channel") val channel: String? = null,
  @SerialName("commit_count") val commitCount: Int? = null,
  @SerialName("commit_sha") val commitSha: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null,
) {
  val isPreview: Boolean
    get() = channel.equals("preview", ignoreCase = true) || tagName.startsWith("preview-r")

  val previewBuildNumber: Int?
    get() =
      if (isPreview) {
        commitCount ?: PREVIEW_TAG_REGEX.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()
      } else {
        null
      }

  val isExperimental: Boolean
    get() = channel.equals("experimental", ignoreCase = true) || tagName.equals("experimental", ignoreCase = true)

  val experimentalBuildNumber: Int?
    get() =
      if (isExperimental) {
        commitCount ?: BUILD_NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
      } else {
        null
      }

  // The experimental release tag is rolling; ignoring one build must not ignore all future ones.
  val updateKey: String
    get() = experimentalBuildNumber?.let { "experimental-r$it" } ?: tagName

  // Old assets stay available during publication. Never select one from a different build.
  val updateAssets: List<Asset>
    get() =
      if (isExperimental) {
        val buildNumber = experimentalBuildNumber
        if (buildNumber == null) emptyList()
        else assets.filter { it.name.startsWith("mpvRx-experimental-r$buildNumber-", ignoreCase = true) }
      } else {
        assets
      }

  companion object {
    private val PREVIEW_TAG_REGEX = Regex("""(?:preview-)?r(\d+)""", RegexOption.IGNORE_CASE)
    private val BUILD_NUMBER_REGEX = Regex("""\br(\d+)\b""", RegexOption.IGNORE_CASE)
  }
}

@Serializable
data class Asset(
  @SerialName("browser_download_url") val downloadUrl: String,
  @SerialName("name") val name: String,
  @SerialName("size") val size: Long,
  @SerialName("content_type") val contentType: String,
)
