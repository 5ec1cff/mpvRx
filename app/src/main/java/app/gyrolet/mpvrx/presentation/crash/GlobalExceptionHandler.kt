/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.gyrolet.mpvrx.presentation.crash

import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** Native crash recovery without a third-party uncaught-exception library. */
class GlobalExceptionHandler(
  private val context: Context,
  private val activity: Class<*>,
) : Thread.UncaughtExceptionHandler {
  private val handlingCrash = AtomicBoolean(false)

  override fun uncaughtException(t: Thread, e: Throwable) {
    if (!handlingCrash.compareAndSet(false, true)) exitProcess(10)
    try {
      val reportId = runCatching { CrashReportStore.capture(context, t, e) }
        .onFailure { Log.e("GlobalExceptionHandler", "Unable to save crash report", it) }
        .getOrNull()
      val stack = runCatching { e.stackTraceToString() }.getOrDefault(e.toString())
      val intent = Intent(context, activity).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        putExtra("exception", stack.take(32 * 1024))
        putExtra("crash_exception_class", e.javaClass.name)
        putExtra("crash_thread", t.name)
        reportId?.let { putExtra("crash_report_id", it) }
      }
      context.startActivity(intent)
    } catch (error: Throwable) {
      Log.e("GlobalExceptionHandler", "Unable to open crash recovery", error)
    } finally {
      exitProcess(10)
    }
  }
}
