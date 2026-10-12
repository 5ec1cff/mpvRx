/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.repository.ai

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AiGenerationOptions(
  val maxTokens: Int = 200,
  val temperature: Double = 0.3,
)

data class AiSource(
  val url: String,
  val title: String? = null,
)

data class AiGeneratedContent(
  val text: String,
  val reasoning: String? = null,
  val sources: List<AiSource> = emptyList(),
)

interface AiClient {
  suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>>

  suspend fun verifyKey(apiKey: String): Result<String>

  suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions = AiGenerationOptions(),
  ): Result<AiGeneratedContent>
}

internal suspend inline fun <T> runCatchingCancellable(crossinline block: suspend () -> T): Result<T> =
  try {
    Result.success(block())
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (error: Exception) {
    Result.failure(error)
  }

/** Timeouts for chat/completion endpoints. */
internal fun OkHttpClient.withAiChatTimeouts(): OkHttpClient =
  newBuilder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

/** Longer read/write timeouts for audio uploads to transcription endpoints. */
internal fun OkHttpClient.withAiUploadTimeouts(): OkHttpClient =
  newBuilder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(180, TimeUnit.SECONDS)
    .build()

internal fun bearerGetRequest(
  url: String,
  apiKey: String,
): Request =
  Request
    .Builder()
    .url(url)
    .header("Authorization", "Bearer $apiKey")
    .get()
    .build()
