/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import app.gyrolet.mpvrx.MainActivity
import app.gyrolet.mpvrx.R
import java.io.File

/**
 * Android does not allow a manifest launcher alias to use runtime artwork.
 * Persist the genuine custom design for previews/Recents and offer an explicit,
 * optional pinned shortcut for the exact image. Never label a shortcut as an app-drawer icon.
 */
internal object CustomIconShortcut {
  private const val ID = "mpvrx.custom-background"
  private const val PREFERENCES = "custom_icon_shortcut"
  private const val IMAGE_FILE = "custom_launcher_background.png"

  enum class Result { Requested, Updated, Unsupported, Failed }

  fun savedColor(context: Context): Int =
    prefs(context).getInt("color", 0xFFC4F5E2.toInt())

  fun savedMark(context: Context, fallback: IconMark): IconMark {
    val name = prefs(context).getString("mark", null)
    return IconMark.entries.firstOrNull { it.name == name } ?: fallback
  }

  /** "preset", "color", or "image"; saved only when a design is applied. */
  fun appliedMode(context: Context): String = prefs(context).getString("active_mode", "preset") ?: "preset"

  fun saveApplied(context: Context, mode: String, color: Int, mark: IconMark) {
    prefs(context).edit()
      .putString("active_mode", mode)
      .putInt("color", color or 0xFF000000.toInt())
      .putString("mark", mark.name)
      .apply()
  }

  fun hasImage(context: Context): Boolean = imageFile(context).isFile

  /**
   * Decode at a bounded resolution, centre-crop, and store in app-private storage.
   * Called off the main thread by the image-picker callback; no storage permission needed.
   */
  fun importImage(context: Context, uri: Uri): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
    val options = BitmapFactory.Options().apply {
      inSampleSize = sample
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, options)
    } ?: error("Unable to decode image")
    val cropped = cropSquare(bitmap, 432)
    if (cropped !== bitmap) bitmap.recycle()
    val target = imageFile(context)
    val staging = File(target.parentFile, "$IMAGE_FILE.tmp")
    staging.outputStream().use { require(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    cropped.recycle()
    if (!staging.renameTo(target)) {
      staging.copyTo(target, overwrite = true)
      staging.delete()
    }
    true
  }.getOrDefault(false)

  fun loadImage(context: Context): Bitmap? =
    runCatching { BitmapFactory.decodeFile(imageFile(context).absolutePath) }.getOrNull()

  private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
  private fun imageFile(context: Context) = File(context.filesDir, IMAGE_FILE)

  private fun cropSquare(source: Bitmap, size: Int): Bitmap {
    val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val side = minOf(source.width, source.height)
    val src = Rect((source.width - side) / 2, (source.height - side) / 2,
      (source.width + side) / 2, (source.height + side) / 2)
    Canvas(result).drawBitmap(source, src, RectF(0f, 0f, size.toFloat(), size.toFloat()),
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return result
  }

  fun render(context: Context, mode: String, color: Int, mark: IconMark, size: Int = 432): Bitmap? {
    val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val image = if (mode == "image") loadImage(context) else null
    if (image != null) {
      canvas.drawBitmap(image, null, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
      image.recycle()
    } else {
      canvas.drawColor(color or 0xFF000000.toInt())
    }
    val foreground = ContextCompat.getDrawable(context, mark.layer)?.mutate() ?: return null
    foreground.setBounds(0, 0, size, size)
    foreground.draw(canvas)
    return result
  }

  /** Existing color-shortcut callers remain compatible. */
  fun apply(context: Context, color: Int, mark: IconMark): Result = apply(context, "color", color, mark)

  /** Optional exact custom home-screen icon; the user must accept the launcher's pin prompt. */
  fun apply(context: Context, mode: String, color: Int, mark: IconMark): Result {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Result.Unsupported
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return Result.Unsupported
    val pinned = manager.pinnedShortcuts.firstOrNull { it.id == ID }
    if (pinned == null && !manager.isRequestPinShortcutSupported) return Result.Unsupported
    if (pinned?.isImmutable == true) return Result.Failed
    if (mode == "image" && !hasImage(context)) return Result.Failed
    val bitmap = render(context, mode, color, mark) ?: return Result.Failed
    val shortcut = ShortcutInfo.Builder(context, ID)
      .setShortLabel(context.getString(R.string.app_name))
      .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
      .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
      .build()
    return if (pinned != null) {
      if (!manager.updateShortcuts(listOf(shortcut))) Result.Failed
      else {
        if (!pinned.isEnabled) manager.enableShortcuts(listOf(ID))
        Result.Updated
      }
    } else {
      if (manager.requestPinShortcut(shortcut, null)) Result.Requested else Result.Failed
    }
  }
}
