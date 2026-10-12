/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.network.client

import android.net.Uri
import app.gyrolet.mpvrx.domain.network.NetworkConnection
import app.gyrolet.mpvrx.domain.network.NetworkFile
import app.gyrolet.mpvrx.domain.network.SmbSharePath
import app.gyrolet.mpvrx.domain.network.SmbUsername
import app.gyrolet.mpvrx.domain.network.normalizedAddress
import app.gyrolet.mpvrx.domain.network.NetworkPath
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.PipeShare
import com.hierynomus.smbj.transport.tcp.async.AsyncDirectTcpTransportFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.EnumSet
import java.util.concurrent.TimeUnit

class SmbClient(
  connection: NetworkConnection,
) : NetworkClient {
  private val connection = connection.normalizedAddress()
  private val sharePath by lazy { SmbSharePath(this.connection.path) }
  companion object {
    // 1 MiB for every transport buffer plus another 1 MiB stream buffer was excessive on phones,
    // especially when mpv opens multiple range requests while seeking. 512 KiB keeps SMBJ reads
    // comfortably large without reserving several megabytes per active connection.
    private const val SMB_TRANSPORT_BUFFER_SIZE = 512 * 1024
    private const val SMB_STREAM_BUFFER_SIZE = 256 * 1024
    private val LOGON_FAILURE_CODES = setOf(0xC000006DL, 0xC000006AL, 0xC0000064L, 0xC0000072L, 0xC0000234L)
    private val AUTH_FAILURE_CODES = LOGON_FAILURE_CODES + 0xC0000022L

    private fun newClient(directoryLeasingEnabled: Boolean): SMBClient =
      SMBClient(
        SmbConfig
          .builder()
          .withTransportLayerFactory(AsyncDirectTcpTransportFactory())
          .withTimeout(60000, TimeUnit.MILLISECONDS)
          .withSoTimeout(15000, TimeUnit.MILLISECONDS)
          .withReadBufferSize(SMB_TRANSPORT_BUFFER_SIZE)
          .withWriteBufferSize(SMB_TRANSPORT_BUFFER_SIZE)
          .withTransactBufferSize(SMB_TRANSPORT_BUFFER_SIZE)
          .withDialects(
            com.hierynomus.mssmb2.SMB2Dialect.SMB_3_1_1,
            com.hierynomus.mssmb2.SMB2Dialect.SMB_3_0_2,
            com.hierynomus.mssmb2.SMB2Dialect.SMB_3_0,
            com.hierynomus.mssmb2.SMB2Dialect.SMB_2_1,
            com.hierynomus.mssmb2.SMB2Dialect.SMB_2_0_2,
          ).withDfsEnabled(false)
          // SMBJ 0.15.0 keys directory leases by relative path, not by the
          // share/tree. Separate shares have identical "" roots on one session.
          // Disable directory leases for multi-share server-root connections;
          // keep the optimized cache for pinned single-share connections.
          .withDirectoryLeasingEnabled(directoryLeasingEnabled)
          .withMultiProtocolNegotiate(true)
          .withSigningRequired(false)
          .withEncryptData(false)
          .build(),
      )
  }

  private var smbClient: SMBClient? = null
  private var smbConnection: Connection? = null
  private var session: Session? = null
  private val connectionMutex = Mutex()

  /**
   * Recursively traverses the exception chain to detect underlying network transport or timeout failures.
   */
  private fun isNetworkError(e: Throwable?): Boolean {
    var current = e
    while (current != null) {
      if (current is java.util.concurrent.TimeoutException ||
        current is com.hierynomus.protocol.transport.TransportException ||
        current is java.net.SocketException ||
        current is java.net.SocketTimeoutException ||
        current is java.io.EOFException
      ) {
        return true
      }
      current = current.cause
    }
    return false
  }

  /**
   * Executes a network operation with a single-retry mechanism.
   * Prevents race conditions during concurrent reconnects via strict session reference tracking.
   */
  private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
    val sessionAtStart = session

    return try {
      if (session == null) {
        throw java.net.SocketException("Session is null on execute")
      }
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (isNetworkError(e)) {
        android.util.Log.w("SmbClient", "SMB transport failed; reconnecting")

        connectionMutex.withLock {
          if (session === sessionAtStart) {
            disconnect()
            connect().getOrThrow()
          }
        }

        block()
      } else {
        throw e
      }
    }
  }

  /**
   * A session may cache several SMB tree connections. Verify the returned
   * tree really belongs to the requested share: showing another share's
   * directory listing (or streaming its files) is never an acceptable fallback.
   */
  private fun Session.connectDiskShare(shareName: String): DiskShare {
    val tree = connectShare(shareName) as? DiskShare
      ?: throw IOException("SMB share is not a disk share: $shareName")
    if (tree.smbPath.shareName?.equals(shareName, ignoreCase = true) != true) {
      throw IOException("SMB server returned a different share instead of $shareName")
    }
    return tree
  }

  override suspend fun connect(): Result<Unit> =
    withContext(Dispatchers.IO) {
      var candidateClient: SMBClient? = null
      var candidateConnection: Connection? = null
      var candidateSession: Session? = null
      try {
        val configuredShare = sharePath.shareName
        val authentication = if (connection.isAnonymous) {
          listOf(AuthenticationContext.anonymous(), AuthenticationContext.guest())
        } else {
          val user = SmbUsername.parse(connection.username)
          listOf(AuthenticationContext(user.username, connection.password.toCharArray(), user.domain))
        }
        withTimeout(15_000) {
          runInterruptible(Dispatchers.IO) {
            for ((index, authContext) in authentication.withIndex()) {
              try {
                val newClient = newClient(directoryLeasingEnabled = configuredShare != null)
                  .also { candidateClient = it }
                val newConnection = newClient.connect(connection.host, connection.port).also { candidateConnection = it }
                val newSession = newConnection.authenticate(authContext).also { candidateSession = it }
                if (configuredShare == null) {
                  // Validate server browsing without enumerating every share again when a
                  // playback/thumbnail client connects. Keep this session-cached tree alive.
                  if (newSession.connectShare("IPC$") !is PipeShare) {
                    throw IOException("SMB server does not support shared-folder browsing")
                  }
                } else {
                  val diskShare = newSession.connectDiskShare(configuredShare)
                  // Keep the session-cached tree alive after validating the configured directory.
                  diskShare.list(sharePath.directory.relative)
                }
                break
              } catch (error: SMBApiException) {
                // Guest and anonymous are distinct SMB logons. Only try both when the user
                // explicitly selected guest access; never fall back from supplied credentials.
                if (index == authentication.lastIndex || error.statusCode !in AUTH_FAILURE_CODES) throw error
                closeResources(candidateSession, candidateConnection, candidateClient)
                candidateSession = null
                candidateConnection = null
                candidateClient = null
              }
            }
          }
        }
        coroutineContext.ensureActive()

        smbClient = candidateClient
        smbConnection = candidateConnection
        session = candidateSession
        candidateClient = null
        candidateConnection = null
        candidateSession = null
        Result.success(Unit)
      } catch (timeout: TimeoutCancellationException) {
        closeResources(candidateSession, candidateConnection, candidateClient)
        Result.failure(IOException("SMB connection timed out"))
      } catch (cancellation: CancellationException) {
        closeResources(candidateSession, candidateConnection, candidateClient)
        throw cancellation
      } catch (error: Exception) {
        closeResources(candidateSession, candidateConnection, candidateClient)
        Result.failure(connectionError(error))
      }
    }

  override suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      val oldSession = session
      val oldConnection = smbConnection
      val oldClient = smbClient
      session = null
      smbConnection = null
      smbClient = null
      closeResources(oldSession, oldConnection, oldClient)
    }
  }

  override fun isConnected(): Boolean = session != null && smbConnection != null

  override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val result =
          executeWithRetry {
            val sess = session ?: throw java.net.SocketException("Not connected")
            val directory = parseNetworkPath(path)
            if (sharePath.isServerRoot && directory.isRoot) {
              return@executeWithRetry withTimeout(15_000) {
                runInterruptible(Dispatchers.IO) { SmbShareDiscovery.listShares(sess) }
              }
            }
            val target = sharePath.resolve(directory)

            val diskShare = sess.connectDiskShare(target.shareName)

            // The DiskShare returned by Session.connectShare() is cached and shared by
            // all requests on this session. Never close it from a per-request operation:
            // closing it here would disconnect the tree out from under active streams.
            val rawFiles: List<FileIdBothDirectoryInformation> =
              try {
                withTimeout(15_000) {
                  runInterruptible(Dispatchers.IO) {
                    // Server-root connections now disable directory leasing entirely,
                    // including SMBJ's lease manager (not merely its list cache).
                    // Pinned single-share connections retain directory caching.
                    diskShare.list(target.path.relative)
                  }
                }
              } catch (_: TimeoutCancellationException) {
                throw IOException("SMB directory listing timed out")
              }

            rawFiles.mapNotNull { fileInfo ->
              val fileName = fileInfo.fileName
              if (fileName == "." || fileName == "..") return@mapNotNull null
              runCatching {
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                NetworkFile(
                  name = fileName,
                  path = directory.child(fileName).value,
                  isDirectory = isDirectory,
                  size = if (isDirectory) 0L else fileInfo.endOfFile,
                  lastModified = fileInfo.lastWriteTime.toEpochMillis(),
                  mimeType = if (!isDirectory) NetworkMimeTypes.forFileName(fileName) else null,
                )
              }.getOrNull()
            }
          }
        Result.success(result)
      } catch (_: TimeoutCancellationException) {
        Result.failure(IOException("SMB directory listing timed out"))
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(connectionError(error))
      }
    }

  override suspend fun getFileStream(
    path: String,
    offset: Long,
  ): Result<InputStream> =
    withContext(Dispatchers.IO) {
      require(offset >= 0L) { "Stream offset must not be negative" }
      try {
        val result =
          executeWithRetry {
            val sess = session ?: throw java.net.SocketException("Not connected")
            val target = sharePath.resolve(parseNetworkPath(path))

            val diskShare = sess.connectDiskShare(target.shareName)

            try {
              val file =
                diskShare.openFile(
                  target.path.relative,
                  EnumSet.of(AccessMask.GENERIC_READ),
                  null,
                  EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                  SMB2CreateDisposition.FILE_OPEN,
                  null,
                )

              val inputStream =
                object : InputStream() {
                  private var currentPosition = offset
                  private var closed = false
                  private var scratch = ByteArray(0)
                  private val singleByte = ByteArray(1)

                  override fun read(): Int {
                    val read = read(singleByte, 0, 1)
                    return if (read == 1) singleByte[0].toInt() and 0xFF else -1
                  }

                  override fun read(b: ByteArray): Int = read(b, 0, b.size)

                  override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                  ): Int {
                    if (off < 0 || len < 0 || off > b.size || len > b.size - off) {
                      throw IndexOutOfBoundsException("offset=$off, length=$len, bufferSize=${b.size}")
                    }
                    if (closed) return -1
                    if (len == 0) return 0

                    try {
                      val readBuffer =
                        if (off == 0 && len == b.size) {
                          b
                        } else {
                          // SMBJ reads up to the supplied array size, so the scratch array must
                          // exactly match the caller's requested length.
                          if (scratch.size != len) scratch = ByteArray(len)
                          scratch
                        }
                      val bytesRead = file.read(readBuffer, currentPosition)
                      if (bytesRead <= 0) {
                        return -1
                      } else {
                        if (readBuffer !== b) {
                          System.arraycopy(readBuffer, 0, b, off, bytesRead)
                        }
                        currentPosition += bytesRead
                        return bytesRead
                      }
                    } catch (error: Exception) {
                      throw IOException("SMB stream read failed", error)
                    }
                  }

                  override fun available(): Int =
                    runCatching {
                      (file.fileInformation.standardInformation.endOfFile - currentPosition)
                        .coerceIn(0L, Int.MAX_VALUE.toLong())
                        .toInt()
                    }.getOrDefault(0)

                  override fun close() {
                    if (closed) return
                    closed = true
                    // Close only the per-request file handle. The DiskShare is the
                    // session-cached tree connect and is shared with concurrent range
                    // streams; closing it here triggers TREE_DISCONNECT and breaks any
                    // sibling stream that is still reading.
                    runCatching { file.close() }
                  }
                }

              BufferedInputStream(inputStream, SMB_STREAM_BUFFER_SIZE)
            } catch (e: Exception) {
              // See above: the cached DiskShare must not be closed here either.
              throw IOException("Failed to open SMB file", e)
            }
          }
        Result.success(result)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }

  override suspend fun getFileSize(path: String): Result<Long> =
    withContext(Dispatchers.IO) {
      try {
        val result =
          executeWithRetry {
            val sess = session ?: throw java.net.SocketException("Not connected")
            val target = sharePath.resolve(parseNetworkPath(path))
            val diskShare = sess.connectDiskShare(target.shareName)

            try {
              val file =
                diskShare.openFile(
                  target.path.relative,
                  EnumSet.of(AccessMask.GENERIC_READ),
                  null,
                  EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                  SMB2CreateDisposition.FILE_OPEN,
                  null,
                )
              file.use { it.fileInformation.standardInformation.endOfFile }
            } catch (e: Exception) {
              throw IOException("Failed to get SMB file size", e)
            }
            // Do not close the session-cached DiskShare here: this size probe can run
            // while another range stream is still using the same tree connect.
          }
        Result.success(result)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }

  /** Credential-free origin URI. Authenticated playback must use the loopback proxy. */
  override suspend fun getFileUri(path: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      try {
        val host = connection.host.trim().removePrefix("[").removeSuffix("]")
        val target = sharePath.resolve(parseNetworkPath(path))
        val uriPath = "/${target.shareName}${if (target.path.isRoot) "" else target.path.value}"
        val uri = URI("smb", null, host, connection.port, uriPath, null, null)
        Result.success(Uri.parse(uri.toASCIIString()))
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }

  private fun parseNetworkPath(path: String): NetworkPath {
    if (!path.startsWith("smb://", ignoreCase = true)) return NetworkPath.from(path)

    val uri = URI(path)
    val address = app.gyrolet.mpvrx.domain.network.NetworkAddress.parse(path, connection.protocol)
    require(address.host.equals(connection.host, ignoreCase = true) &&
      (address.port ?: 445) == connection.port
    ) {
      "SMB path belongs to a different server"
    }
    return sharePath.fromServerPath(NetworkPath.from(uri.path.orEmpty()))
  }

  private fun connectionError(error: Exception): Exception = when {
    error is SMBApiException && error.statusCode in LOGON_FAILURE_CODES ->
      NetworkAuthenticationException("SMB login failed. Check the username, password and DOMAIN\\username.", error)
    error is SMBApiException && error.statusCode == 0xC0000022L ->
      NetworkAuthenticationException("SMB access denied. Check account permissions for this share and folder.", error)
    error is SMBApiException && error.statusCode == 0xC00000CCL ->
      IOException("SMB share not found. Enter a shared folder such as /Media, not a local disk path.", error)
    error is SMBApiException && error.statusCode in setOf(0xC0000034L, 0xC000003AL) ->
      IOException("The configured folder does not exist inside the SMB share.", error)
    else -> error
  }

  private fun closeResources(
    smbSession: Session?,
    networkConnection: Connection?,
    client: SMBClient?,
  ) {
    runCatching { smbSession?.close() }
    runCatching { networkConnection?.close() }
    runCatching { client?.close() }
  }
}
