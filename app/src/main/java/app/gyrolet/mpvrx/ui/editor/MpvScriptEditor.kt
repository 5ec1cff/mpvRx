/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.editor

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.gyrolet.mpvrx.utils.clipboard.SafeClipboard
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorReleaseEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.subscribeAlways
import org.eclipse.tm4e.core.registry.IThemeSource

@Composable
fun MpvScriptEditor(
  content: String,
  onContentChange: (String) -> Unit,
  language: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val colors = androidx.compose.material3.MaterialTheme.colorScheme
  val selectionColors = LocalTextSelectionColors.current
  // Match the in-app theme even when it differs from the system's light/dark setting.
  val isDarkTheme = colors.background.luminance() < 0.5f
  val latestOnContentChange by rememberUpdatedState(onContentChange)
  var applyingExternalText by remember { mutableStateOf(false) }
  var textSize by rememberSaveable { mutableStateOf(14f) }
  var wordWrap by rememberSaveable { mutableStateOf(true) }
  var showSearch by rememberSaveable { mutableStateOf(false) }
  var findText by rememberSaveable { mutableStateOf("") }
  var replaceText by rememberSaveable { mutableStateOf("") }
  var showGoToLine by rememberSaveable { mutableStateOf(false) }
  var targetLine by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(Unit) {
    // Lazy, offline-first manual loading: no network work on the video startup path.
    MpvDocumentationRepository.ensureLoaded(context)
    ScriptEditorTextMate.ensureInitialized(context)
  }

  val editor =
    remember {
      ScriptEditorTextMate.ensureInitialized(context)
      SafeCodeEditor(context).apply {
        setTextSize(textSize)
        setUndoEnabled(true)
        setTabWidth(4)
        setWordwrap(wordWrap)
        setHighlightCurrentLine(true)
        setHighlightBracketPair(true)
        setScalable(true)
        typefaceText = Typeface.MONOSPACE
        typefaceLineNumber = Typeface.MONOSPACE
        setPinLineNumber(true)
        // Sora's native scrollbars remain inside the editor viewport, not underneath the
        // keyboard toolbar. The horizontal track is only useful if wrapping is disabled.
        setScrollBarEnabled(true)
        setHorizontalScrollBarEnabled(!wordWrap)
        editable = true
        colorScheme = createEditorColorScheme(isDarkTheme)
        colorScheme.applyMpvColors(
          colors = colors,
          selectionBackground = selectionColors.backgroundColor.toArgb(),
        )
        setEditorLanguage(language.toTextMateLanguage())
        subscribeAlways<ContentChangeEvent> { event ->
          if (!applyingExternalText) {
            latestOnContentChange(event.editor.text.toString())
          }
        }
      }
    }

  DisposableEffect(editor) {
    onDispose {
      editor.searcher.stopSearch()
      editor.release()
    }
  }

  LaunchedEffect(wordWrap) {
    editor.setWordwrap(wordWrap)
    editor.setHorizontalScrollBarEnabled(!wordWrap)
  }

  LaunchedEffect(textSize) {
    editor.setTextSize(textSize)
  }

  LaunchedEffect(showSearch, findText) {
    if (!showSearch || findText.isBlank()) {
      editor.searcher.stopSearch()
    } else {
      delay(160)
      editor.searcher.search(findText, EditorSearcher.SearchOptions(true, false))
    }
  }

  LaunchedEffect(isDarkTheme, colors, selectionColors) {
    editor.colorScheme = createEditorColorScheme(isDarkTheme)
    editor.colorScheme.applyMpvColors(
      colors = colors,
      selectionBackground = selectionColors.backgroundColor.toArgb(),
    )
  }

  LaunchedEffect(language) {
    editor.setEditorLanguage(language.toTextMateLanguage())
  }

  LaunchedEffect(content) {
    val editorText = editor.text.toString()
    if (editorText != content) {
      applyingExternalText = true
      editor.setText(content)
      applyingExternalText = false
    }
  }

  Column(modifier = modifier) {
    // The code viewport takes the remaining height, and tools are docked at the IME edge
    // rather than covering the first lines of text or occupying a permanently tall header.
    AndroidView(
      factory = {
        ScriptEditorTextMate.ensureInitialized(it)
        editor
      },
      modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    HorizontalDivider(color = colors.outlineVariant)
    Surface(
      color = colors.surfaceContainerLow,
      tonalElevation = 1.dp,
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        if (showSearch) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            OutlinedTextField(
              value = findText,
              onValueChange = { findText = it },
              placeholder = { Text("Find") },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { if (findText.isNotBlank()) editor.searcher.gotoPrevious() },
              contentPadding = PaddingValues(horizontal = 6.dp)) { Text("↑") }
            TextButton(onClick = { if (findText.isNotBlank()) editor.searcher.gotoNext() },
              contentPadding = PaddingValues(horizontal = 6.dp)) { Text("↓") }
          }
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            OutlinedTextField(
              value = replaceText,
              onValueChange = { replaceText = it },
              placeholder = { Text("Replace with") },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = {
                if (findText.isNotBlank()) {
                  if (editor.searcher.isMatchedPositionSelected) {
                    editor.searcher.replaceCurrentMatch(replaceText)
                  } else editor.searcher.gotoNext()
                }
              },
              contentPadding = PaddingValues(horizontal = 6.dp),
            ) { Text("Replace", style = MaterialTheme.typography.labelMedium) }
            TextButton(
              onClick = { if (findText.isNotBlank()) editor.searcher.replaceAll(replaceText) },
              contentPadding = PaddingValues(horizontal = 6.dp),
            ) { Text("All", style = MaterialTheme.typography.labelMedium) }
          }
        }
        if (showGoToLine) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = targetLine,
              onValueChange = { targetLine = it.filter(Char::isDigit).take(7) },
              placeholder = { Text("Line number") },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
              targetLine.toIntOrNull()?.let {
                editor.jumpToLine((it - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0)))
              }
              showGoToLine = false
            }) { Text("Go") }
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          TextButton(onClick = { if (editor.canUndo()) editor.undo() }) { Text("Undo") }
          TextButton(onClick = { if (editor.canRedo()) editor.redo() }) { Text("Redo") }
          TextButton(onClick = { showSearch = !showSearch }) { Text(if (showSearch) "Close" else "Find") }
          TextButton(onClick = { wordWrap = !wordWrap }) { Text(if (wordWrap) "No wrap" else "Wrap") }
          TextButton(onClick = { editor.selectAll() }) { Text("Select all") }
          TextButton(onClick = { editor.insertText("    ", 4) }) { Text("Tab") }
          TextButton(onClick = { editor.insertText("#", 1) }) { Text("#") }
          TextButton(onClick = { showGoToLine = !showGoToLine }) { Text("Line") }
          TextButton(onClick = { textSize = (textSize - 1f).coerceAtLeast(10f) }) { Text("A−") }
          TextButton(onClick = { textSize = (textSize + 1f).coerceAtMost(24f) }) { Text("A+") }
        }
      }
    }
  }
}

private class SafeCodeEditor(
  context: Context,
) : CodeEditor(context) {
  init {
    replaceComponent(EditorAutoCompletion::class.java, AboveCaretAutoCompletion(this))
  }

  override fun copyTextToClipboard(
    text: CharSequence,
    start: Int,
    end: Int,
  ) {
    val textToCopy = if (end > start) text.subSequence(start, end) else text
    SafeClipboard.copyPlainText(context, "Editor selection", textToCopy, showToast = true)
  }
}

private class AboveCaretAutoCompletion(
  private val codeEditor: CodeEditor,
) : EditorAutoCompletion(codeEditor) {
  private val visibleEditor = Rect()
  private val visibleWindow = Rect()
  private val screenLocation = IntArray(2)
  private var showRequested = false
  private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
    if (isShowing || showRequested) updateCompletionWindowPosition(false)
  }

  init {
    codeEditor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    codeEditor.subscribeAlways<EditorReleaseEvent> {
      if (codeEditor.viewTreeObserver.isAlive) codeEditor.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
    }
  }

  override fun updateCompletionWindowPosition(scrollEditor: Boolean) {
    val fullWidth = completionWndPositionMode == WINDOW_POS_MODE_FULL_WIDTH_ALWAYS ||
      completionWndPositionMode == WINDOW_POS_MODE_AUTO && codeEditor.width < 500 * codeEditor.dpUnit
    val desiredWidth = if (fullWidth) codeEditor.width * 7 / 8 else minOf((300 * codeEditor.dpUnit).toInt(), codeEditor.width / 2)
    setSize(desiredWidth, height)
  }

  override fun setSize(width: Int, height: Int) {
    if (!codeEditor.getLocalVisibleRect(visibleEditor)) {
      setMaxHeight(0)
      super.setSize(0, 0)
      hide()
      return
    }
    codeEditor.getWindowVisibleDisplayFrame(visibleWindow)
    codeEditor.getLocationOnScreen(screenLocation)
    val top = maxOf(visibleEditor.top, visibleWindow.top - screenLocation[1])
    val bottom = minOf(visibleEditor.bottom, visibleWindow.bottom - screenLocation[1])
    val cursor = codeEditor.cursor
    val caretBottom = (codeEditor.layout.getCharLayoutOffset(cursor.rightLine, cursor.rightColumn)[0] - codeEditor.offsetY).toInt()
    val caretTop = caretBottom - codeEditor.rowHeight
    val gap = (8 * codeEditor.dpUnit).toInt()
    val above = (caretTop - gap - top).coerceAtLeast(0)
    val below = (bottom - caretBottom - gap).coerceAtLeast(0)
    val minimumHeight = maxOf(codeEditor.rowHeight, (48 * codeEditor.dpUnit).toInt())
    val placeAbove = above >= minimumHeight
    val available = minOf(if (placeAbove) above else below, (200 * codeEditor.dpUnit).toInt())
    if (caretTop < top || caretBottom > bottom || available < minimumHeight) {
      setMaxHeight(0)
      super.setSize(0, 0)
      hide()
      return
    }
    setMaxHeight(available)
    val popupWidth = width.coerceIn(0, visibleEditor.width())
    val popupHeight = height.coerceIn(0, available)
    val fullWidth = completionWndPositionMode == WINDOW_POS_MODE_FULL_WIDTH_ALWAYS ||
      completionWndPositionMode == WINDOW_POS_MODE_AUTO && codeEditor.width < 500 * codeEditor.dpUnit
    val desiredLeft = if (fullWidth) (codeEditor.width - popupWidth) / 2 else codeEditor.updateCursorAnchor().toInt()
    val left = desiredLeft.coerceIn(visibleEditor.left, visibleEditor.right - popupWidth)
    val popupTop = if (placeAbove) caretTop - gap - popupHeight else caretBottom + gap
    super.setSize(popupWidth, popupHeight)
    setLocationAbsolutely(left, popupTop)
  }

  override fun show() {
    showRequested = true
    updateCompletionWindowPosition(false)
    if (showRequested && width > 0 && height > 0) super.show()
  }

  override fun hide() {
    showRequested = false
    super.hide()
  }
}

private object ScriptEditorTextMate {
  @Volatile
  private var initialized = false

  fun ensureInitialized(context: Context) {
    if (initialized) return

    synchronized(this) {
      if (initialized) return

      runCatching {
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

        val themeRegistry = ThemeRegistry.getInstance()
        listOf("darcula", "quietlight").forEach { themeName ->
          val path = "textmate/$themeName.json"
          themeRegistry.loadTheme(
            ThemeModel(
              IThemeSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream(path),
                path,
                null,
              ),
              themeName,
            ),
          )
        }
        initialized = true
      }.onFailure { error ->
        Log.w("MpvScriptEditor", "TextMate assets failed to initialize", error)
      }
    }
  }

  fun setTheme(isDarkTheme: Boolean) {
    runCatching {
      ThemeRegistry.getInstance().setTheme(if (isDarkTheme) "darcula" else "quietlight")
    }
  }
}

private fun createEditorColorScheme(isDarkTheme: Boolean): EditorColorScheme {
  ScriptEditorTextMate.setTheme(isDarkTheme)
  return TextMateColorScheme.create(ThemeRegistry.getInstance())
}

private fun String.toTextMateLanguage(): Language {
  val normalizedLanguage = lowercase()
  val scopeName =
    when (normalizedLanguage) {
      "lua" -> "source.lua"
      "js", "javascript", "json" -> "source.js"
      "xml" -> "text.xml"
      "mpv.conf", "mpv-conf", "mpv_config" -> "source.mpv.conf"
      "input.conf", "input-conf", "input_config" -> "source.mpv.input"
      else -> null
    }
  val completionMode =
    when (normalizedLanguage) {
      "lua", "js", "javascript" -> MpvCompletionMode.SCRIPT
      "mpv.conf", "mpv-conf", "mpv_config" -> MpvCompletionMode.MPV_CONF
      "input.conf", "input-conf", "input_config" -> MpvCompletionMode.INPUT_CONF
      else -> null
    }

  val baseLanguage =
    scopeName
      ?.let {
        runCatching {
          TextMateLanguage.create(it, false)
        }.getOrNull()
      }
      ?: EmptyLanguage()

  return completionMode
    ?.let { MpvLanguageWrapper(baseLanguage, it) }
    ?: baseLanguage
}

private class MpvLanguageWrapper(
  private val base: Language,
  private val completionMode: MpvCompletionMode,
) : Language by base {
  override fun requireAutoComplete(
    content: ContentReference,
    position: CharPosition,
    publisher: CompletionPublisher,
    extraArguments: Bundle,
  ) {
    base.requireAutoComplete(content, position, publisher, extraArguments)
    val prefix = CompletionHelper.computePrefix(content, position, ::isMpvCompletionChar)
    val beforeCaret = content.getLine(position.line).take(position.column).toString()
    val assignment =
      if (completionMode == MpvCompletionMode.MPV_CONF &&
        !beforeCaret.trimStart().startsWith("#")
      ) {
        Regex("""^\s*(?:--)?([a-zA-Z][a-zA-Z0-9_-]*)\s*=\s*([^\s#]*)$""")
          .matchEntire(beforeCaret)
      } else null
    val optionName = assignment?.groupValues?.get(1)
    val optionValuePrefix = assignment?.groupValues?.get(2)?.substringAfterLast(',').orEmpty()
    val inputKeyPosition =
      if (completionMode == MpvCompletionMode.INPUT_CONF) {
        val beforeCaret = beforeCaret.trimStart()
        // The first token of an input.conf binding is a key combination. After its
        // separating whitespace, complete mpv command names instead of suggesting keys.
        beforeCaret.none(Char::isWhitespace)
      } else {
        false
      }
    MpvAutoCompleteProvider.provideCompletion(
      prefix = prefix,
      mode = completionMode,
      publisher = publisher,
      inputKeyPosition = inputKeyPosition,
      optionName = optionName,
      optionValuePrefix = optionValuePrefix,
    )
  }
}

private fun isMpvCompletionChar(ch: Char): Boolean =
  ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.' || ch == '/' || ch == '+'

private fun EditorColorScheme.applyMpvColors(
  colors: androidx.compose.material3.ColorScheme,
  selectionBackground: Int,
) {
  setColor(EditorColorScheme.WHOLE_BACKGROUND, colors.background.toArgb())
  setColor(EditorColorScheme.TEXT_NORMAL, colors.onBackground.toArgb())
  setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, colors.surfaceContainerLowest.toArgb())
  setColor(EditorColorScheme.LINE_NUMBER, colors.onSurfaceVariant.copy(alpha = 0.62f).toArgb())
  setColor(EditorColorScheme.LINE_NUMBER_CURRENT, colors.primary.toArgb())
  setColor(EditorColorScheme.LINE_DIVIDER, colors.outlineVariant.copy(alpha = 0.55f).toArgb())
  setColor(EditorColorScheme.CURRENT_LINE, colors.surfaceContainerHighest.copy(alpha = 0.42f).toArgb())
  setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionBackground)
  setColor(EditorColorScheme.SELECTION_INSERT, colors.primary.toArgb())
  setColor(EditorColorScheme.SELECTION_HANDLE, colors.primary.toArgb())
  setColor(EditorColorScheme.SCROLL_BAR_THUMB, colors.primary.copy(alpha = 0.72f).toArgb())
  setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, colors.primary.toArgb())
  setColor(EditorColorScheme.SCROLL_BAR_TRACK, colors.surfaceVariant.copy(alpha = 0.18f).toArgb())
  setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, colors.surfaceContainerHigh.toArgb())
  setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, colors.primaryContainer.toArgb())
  setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, colors.onSurface.toArgb())
  setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, colors.onSurfaceVariant.toArgb())
  setColor(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED, colors.primary.toArgb())
}
