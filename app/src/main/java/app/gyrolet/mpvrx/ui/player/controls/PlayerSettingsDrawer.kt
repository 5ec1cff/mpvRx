/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.player.controls

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.preference.PreferenceStore
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.components.InlineSearchBar
import app.gyrolet.mpvrx.ui.icons.AppIcon
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.PlayerViewModel
import app.gyrolet.mpvrx.ui.player.controls.components.panels.DraggablePanel
import app.gyrolet.mpvrx.ui.player.controls.components.panels.SubtitleSettingsColorsCard
import app.gyrolet.mpvrx.ui.player.controls.components.panels.SubtitleSettingsTypographyCard
import app.gyrolet.mpvrx.ui.player.controls.components.panels.SubtitlesMiscellaneousCard
import app.gyrolet.mpvrx.ui.player.controls.components.panels.VideoSettingsDebandCard
import app.gyrolet.mpvrx.ui.player.controls.components.panels.VideoSettingsFilterPresetsCard
import app.gyrolet.mpvrx.ui.player.controls.components.panels.VideoSettingsFiltersCard
import app.gyrolet.mpvrx.ui.preferences.AudioPreferencesScreen
import app.gyrolet.mpvrx.ui.preferences.ControlLayoutEditorScreen
import app.gyrolet.mpvrx.ui.preferences.GesturePreferencesScreen
import app.gyrolet.mpvrx.ui.preferences.LiquidGlassPreferencesScreen
import app.gyrolet.mpvrx.ui.preferences.LocalLivePlayerSettingsContent
import app.gyrolet.mpvrx.ui.preferences.LocalPlayerSettingsPanel
import app.gyrolet.mpvrx.ui.preferences.LocalPlayerSettingsConfigOptions
import app.gyrolet.mpvrx.ui.preferences.PersistentStatusBarPreferencesScreen
import app.gyrolet.mpvrx.ui.preferences.PlayerControlsPreferencesScreen
import app.gyrolet.mpvrx.ui.preferences.PlayerPreferencesScreen
import app.gyrolet.mpvrx.ui.preferences.SearchablePreference
import app.gyrolet.mpvrx.ui.preferences.SearchablePreferences
import app.gyrolet.mpvrx.ui.preferences.SettingsSearchNavigation
import app.gyrolet.mpvrx.ui.preferences.SubtitlesPreferencesScreen
import app.gyrolet.mpvrx.ui.preferences.searchTargetKey
import app.gyrolet.mpvrx.ui.preferences.searchCategoryTitleRes
import app.gyrolet.mpvrx.ui.preferences.isLivePlayerSetting
import app.gyrolet.mpvrx.ui.liquidglass.liquidGlassActive
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.theme.ConnectedLayout
import app.gyrolet.mpvrx.ui.theme.rememberConnectedShape
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.ScreenNavDisplay
import app.gyrolet.mpvrx.ui.utils.navigateTo
import app.gyrolet.mpvrx.ui.utils.popSafely
import app.gyrolet.mpvrx.ui.utils.currentMpvConfigOverrideOptions
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
private object PlayerSettingsHome : Screen {
  @Composable
  override fun Content() = Unit
}

private data class SettingsCategory(
  @StringRes val title: Int,
  val icon: AppIcon,
  val screen: Screen,
  @StringRes val target: Int? = null,
)

private fun playerSettingsCategories() = listOf(
  SettingsCategory(R.string.pref_player, Icons.RoundedFilled.Settings, PlayerPreferencesScreen),
  SettingsCategory(R.string.pref_layout_title, Icons.RoundedFilled.GridView, PlayerControlsPreferencesScreen),
  SettingsCategory(R.string.player_sheets_video_settings_title, Icons.RoundedFilled.Tune, PlayerLiveSettings.Picture),
  SettingsCategory(
    R.string.pref_audio, Icons.RoundedFilled.Audiotrack, AudioPreferencesScreen,
    R.string.pref_audio_pitch_correction_title,
  ),
  SettingsCategory(
    R.string.player_settings_subtitle_appearance, Icons.RoundedFilled.Subtitles, PlayerLiveSettings.SubtitleStyle,
  ),
  SettingsCategory(R.string.pref_subtitles, Icons.RoundedFilled.Subtitles, SubtitlesPreferencesScreen),
  SettingsCategory(R.string.pref_gesture, Icons.RoundedFilled.Gesture, GesturePreferencesScreen),
  SettingsCategory(
    R.string.pref_persistent_status_title, Icons.RoundedFilled.AccessTime, PersistentStatusBarPreferencesScreen,
  ),
  SettingsCategory(R.string.pref_appearance_liquid_glass_title, Icons.RoundedFilled.Tune, LiquidGlassPreferencesScreen),
)

/** Both native adjustment pages reuse the exact controls and config guards of the existing panels. */
@Serializable
private enum class PlayerLiveSettings : Screen {
  Picture, SubtitleStyle;

  @Composable
  override fun Content() = Unit // Rendered by the host with the active player's ViewModel.
}

@Composable
internal fun PlayerSettingsDrawer(
  controlsVisible: Boolean,
  panelVisible: Boolean,
  onPanelVisibilityChanged: (Boolean) -> Unit,
  viewModel: PlayerViewModel,
) {
  val reducedMotion = AppMotion.playerReducedMotion()
  @Suppress("UNCHECKED_CAST")
  val backStack = rememberNavBackStack(PlayerSettingsHome) as NavBackStack<Screen>
  val savedContent = rememberSaveableStateHolder()
  val glassActive = liquidGlassActive()
  val categories = remember(glassActive) {
    playerSettingsCategories().filter { it.screen != LiquidGlassPreferencesScreen || glassActive }
  }
  LaunchedEffect(categories) {
    val supportedScreens = categories.map { it.screen }.toSet()
    Snapshot.withMutableSnapshot {
      backStack.removeAll {
        it != PlayerSettingsHome && it !is ControlLayoutEditorScreen && it !in supportedScreens
      }
      if (backStack.isEmpty()) backStack.add(PlayerSettingsHome)
    }
  }
  val resources = LocalResources.current
  val keyboard = LocalSoftwareKeyboardController.current
  var searchMode by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  var expanded by rememberSaveable { mutableStateOf(false) }
  val panelWidth by animateDpAsState(
    targetValue = if (expanded) 520.dp else 420.dp,
    animationSpec = if (reducedMotion) snap() else AppMotion.Spatial.StandardDp,
    label = "playerSettingsWidth",
  )
  val panelHeightFraction by animateFloatAsState(
    targetValue = if (expanded) 0.94f else 0.78f,
    animationSpec = if (reducedMotion) snap() else AppMotion.Spatial.Standard,
    label = "playerSettingsHeight",
  )
  val close: () -> Unit = {
    keyboard?.hide()
    onPanelVisibilityChanged(false)
  }
  val back: () -> Unit = {
    if (searchMode || query.isNotBlank()) {
      searchMode = false
      query = ""
      keyboard?.hide()
    } else if (backStack.size > 1) {
      backStack.popSafely()
    } else {
      close()
    }
  }
  BackHandler(enabled = panelVisible, onBack = back)

  Box(Modifier.fillMaxSize().imePadding()) {
    AnimatedVisibility(
      visible = controlsVisible && !panelVisible,
      modifier = Modifier.align(AbsoluteAlignment.CenterLeft)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal)),
      enter = fadeIn(tween(100)),
      exit = fadeOut(tween(80)),
    ) {
      PlayerControlEdgeHandle(
        enabled = controlsVisible,
        onOpen = { onPanelVisibilityChanged(true) },
        openFromLeft = true,
        label = stringResource(R.string.player_settings_open),
      )
    }
    AnimatedVisibility(
      visible = panelVisible,
      enter = if (reducedMotion) {
        fadeIn(tween(90))
      } else {
        fadeIn(tween(140)) + slideInHorizontally(tween(220)) { -it / 3 }
      },
      exit = if (reducedMotion) {
        fadeOut(tween(70))
      } else {
        fadeOut(tween(100)) + slideOutHorizontally(tween(180)) { -it / 3 }
      },
    ) {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = close,
        ))
        savedContent.SaveableStateProvider("player-settings-panel") {
          DraggablePanel(
            modifier = Modifier.semantics { paneTitle = resources.getString(R.string.player_settings_title) },
            alignment = AbsoluteAlignment.CenterLeft,
            scrollContent = false,
            portraitHeightFraction = panelHeightFraction,
            maxPanelWidth = panelWidth,
            allowVerticalDrag = true,
            consumeBackgroundTouches = true,
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            header = {
              val current = backStack.lastOrNull()
              val title = if (searchMode || current == PlayerSettingsHome) {
                resources.getString(R.string.player_settings_title)
              } else {
                categories.firstOrNull { it.screen == current }?.let { resources.getString(it.title) }
                  ?: resources.getString(R.string.pref_layout_title)
              }
              SettingsPanelHeader(
                title = title,
                canGoBack = searchMode || query.isNotBlank() || backStack.size > 1,
                expanded = expanded,
                onBack = back,
                onSearch = { searchMode = true },
                onExpand = { expanded = !expanded },
                onClose = close,
              )
            },
          ) {
            val colors = MaterialTheme.colorScheme
            MaterialTheme(colorScheme = colors.copy(background = Color.Transparent)) {
              CompositionLocalProvider(
                LocalBackStack provides backStack,
                LocalPlayerSettingsPanel provides true,
                LocalPlayerSettingsConfigOptions provides currentMpvConfigOverrideOptions(),
              ) {
                Box(Modifier.fillMaxSize()) {
                  ScreenNavDisplay(backStack = backStack, onBack = back) { screen ->
                    when (screen) {
                      PlayerSettingsHome -> SettingsPanelHome(categories, query, { query = it }, backStack) {
                        query = ""
                        keyboard?.hide()
                      }
                      is PlayerLiveSettings -> LiveSettingsContent(screen, viewModel)
                      else -> screen.Content()
                    }
                  }
                  // Keep the current page mounted beneath search, preserving its scroll position
                  // and any expanded sections when search is dismissed without choosing a result.
                  if (searchMode) {
                    Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
                      SettingsPanelHome(categories, query, { query = it }, backStack, requestSearchFocus = true) {
                        searchMode = false
                        query = ""
                        keyboard?.hide()
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsPanelHeader(
  title: String,
  canGoBack: Boolean,
  expanded: Boolean,
  onBack: () -> Unit,
  onSearch: () -> Unit,
  onExpand: () -> Unit,
  onClose: () -> Unit,
) {
  val reducedMotion = AppMotion.playerReducedMotion()
  val expandRotation by animateFloatAsState(
    targetValue = if (expanded) 0f else 180f,
    animationSpec = if (reducedMotion) snap() else tween(180),
    label = "playerSettingsExpandIcon",
  )
  Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    if (canGoBack) {
      IconButton(onClick = onBack) { Icon(Icons.RoundedFilled.ArrowBack, stringResource(R.string.back)) }
    } else {
      Icon(Icons.RoundedFilled.Settings, null, Modifier.padding(12.dp).size(24.dp))
    }
    Text(
      title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
      maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    IconButton(onClick = onSearch) { Icon(Icons.RoundedFilled.Search, stringResource(R.string.settings_search_title)) }
    IconButton(onClick = onExpand) {
      Icon(
        Icons.RoundedFilled.ExpandMore,
        stringResource(if (expanded) R.string.player_settings_compact else R.string.player_settings_expand),
        Modifier.rotate(expandRotation),
      )
    }
    IconButton(onClick = onClose) { Icon(Icons.RoundedFilled.Close, stringResource(R.string.player_settings_close)) }
  }
}

@Composable
private fun SettingsPanelHome(
  categories: List<SettingsCategory>,
  query: String,
  onQueryChange: (String) -> Unit,
  backStack: NavBackStack<Screen>,
  requestSearchFocus: Boolean = false,
  onNavigate: () -> Unit,
) {
  val resources = LocalResources.current
  val searchFocus = remember { FocusRequester() }
  LaunchedEffect(requestSearchFocus) {
    if (requestSearchFocus) searchFocus.requestFocus()
  }
  val store = koinInject<PreferenceStore>()
  val favoritesPreference = remember(store) { store.getStringSet("player_settings_favorites", emptySet()) }
  val favorites by favoritesPreference.collectAsState()
  val screens = remember(categories) { categories.map { it.screen }.toSet() }
  val categoryEntries = remember(categories) {
    categories.map {
      SearchablePreference(
        titleRes = it.title,
        category = "Player",
        screen = it.screen,
        targetRes = it.target,
        keywords = when (it.screen) {
          PlayerLiveSettings.Picture -> listOf(
            "brightness", "contrast", "saturation", "gamma", "hue", "sharpness", "deband", "filters", "presets",
          )
          PlayerLiveSettings.SubtitleStyle -> listOf(
            "subtitles", "font", "size", "color", "outline", "shadow", "bold", "italic", "position", "scale",
          )
          else -> emptyList()
        },
      )
    }
  }
  val entries = remember(screens) {
    (SearchablePreferences.forScreens(screens).filter {
      isLivePlayerSetting(it.screen, it.targetRes ?: it.titleRes)
    } + categoryEntries).distinctBy { it.favoriteKey() }
  }
  val results = remember(query, resources, screens, categoryEntries) {
    val matches = SearchablePreferences.search(query, includeSecureFolder = false, screens = screens) {
      resources.getString(it)
    }.map { it.preference }.filter {
      it.screen in screens && isLivePlayerSetting(it.screen, it.targetRes ?: it.titleRes)
    }
    val terms = query.trim().split(Regex("\\s+"))
    val matchingCategories = categoryEntries.filter { entry ->
      val text = entry.titleRes?.let { resources.getString(it) }.orEmpty() + " " + entry.keywords.joinToString(" ")
      query.isNotBlank() && terms.all { text.contains(it, ignoreCase = true) }
    }
    (matchingCategories.filter { it.screen is PlayerLiveSettings } + matches + matchingCategories)
      .distinctBy { it.favoriteKey() }
  }
  fun showScreen(screen: Screen) {
    // Search can revisit a page already in this drawer's history. Reuse that entry instead of
    // accumulating duplicate navigation keys or losing the page's existing state.
    Snapshot.withMutableSnapshot {
      val existing = backStack.indexOf(screen)
      if (existing >= 0) {
        while (backStack.lastIndex > existing) backStack.removeAt(backStack.lastIndex)
      } else {
        backStack.navigateTo(screen)
      }
    }
  }
  fun navigateToSetting(entry: SearchablePreference) {
    SettingsSearchNavigation.open(entry)
    showScreen(entry.screen)
    onNavigate()
  }
  Column(Modifier.fillMaxSize()) {
    InlineSearchBar(
      query = query,
      onQueryChange = onQueryChange,
      onSearch = onQueryChange,
      inputFieldModifier = Modifier.focusRequester(searchFocus),
      placeholder = { Text(stringResource(R.string.player_settings_search)) },
      leadingIcon = { Icon(Icons.RoundedFilled.Search, null) },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { onQueryChange("") }) {
            Icon(Icons.RoundedFilled.Close, stringResource(R.string.player_settings_clear_search))
          }
        }
      },
      windowInsets = WindowInsets(0),
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (query.isNotBlank()) {
        if (results.isEmpty()) item {
          Text(stringResource(R.string.settings_search_no_results), Modifier.padding(12.dp))
        }
        itemsIndexed(results, key = { _, item -> item.favoriteKey() }) { index, entry ->
          SettingsResultRow(entry, index, results.size, entry.favoriteKey() in favorites,
            onOpen = { navigateToSetting(entry) },
            onFavorite = {
              val key = entry.favoriteKey()
              favoritesPreference.set(if (key in favorites) favorites - key else favorites + key)
            },
          )
        }
      } else {
        item {
          Text(stringResource(R.string.player_settings_live_hint), Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val pinned = entries.filter { it.favoriteKey() in favorites }
        if (pinned.isNotEmpty()) {
          item {
            Text(stringResource(R.string.player_settings_favorites), Modifier.padding(8.dp),
              style = MaterialTheme.typography.labelLarge)
          }
          itemsIndexed(pinned, key = { _, item -> item.favoriteKey() }) { index, entry ->
            SettingsResultRow(entry, index, pinned.size, true, { navigateToSetting(entry) }, {
              favoritesPreference.set(favorites - entry.favoriteKey())
            })
          }
        }
        item {
          Text(stringResource(R.string.player_settings_categories), Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelLarge)
        }
        itemsIndexed(categories) { index, category ->
          Surface(
            onClick = {
              category.target?.let { target ->
                SettingsSearchNavigation.open(
                  SearchablePreference(titleRes = target, category = "Player", screen = category.screen),
                )
              }
              showScreen(category.screen)
              onNavigate()
            },
            shape = rememberConnectedShape(index, categories.size, ConnectedLayout.Vertical),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
          ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              Icon(category.icon, null, Modifier.size(24.dp))
              Text(stringResource(category.title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
              Icon(Icons.RoundedFilled.ChevronRight, null, Modifier.size(20.dp))
            }
          }
        }
        if (pinned.isEmpty()) item {
          Text(stringResource(R.string.player_settings_pin_hint), Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

private fun SearchablePreference.favoriteKey() = "${screen.javaClass.name}:$searchTargetKey"

@Composable
private fun SettingsResultRow(
  entry: SearchablePreference,
  index: Int,
  count: Int,
  favorite: Boolean,
  onOpen: () -> Unit,
  onFavorite: () -> Unit,
) {
  val resources = LocalResources.current
  val pinLabel = stringResource(if (favorite) R.string.player_settings_unpin else R.string.player_settings_pin)
  Surface(
    onClick = onOpen,
    shape = rememberConnectedShape(index, count, ConnectedLayout.Vertical),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Row(
      Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          entry.titleRes?.let { resources.getString(it) } ?: entry.title.orEmpty(),
          style = MaterialTheme.typography.bodyLarge,
        )
        Text(stringResource(searchCategoryTitleRes(entry.category)),
          style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton(onClick = onFavorite, modifier = Modifier.semantics { stateDescription = pinLabel }) {
        Icon(if (favorite) Icons.RoundedFilled.Favorite else Icons.RoundedFilled.FavoriteBorder, pinLabel,
          tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun LiveSettingsContent(screen: PlayerLiveSettings, viewModel: PlayerViewModel) {
  CompositionLocalProvider(LocalLivePlayerSettingsContent provides true) {
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      when (screen) {
        PlayerLiveSettings.Picture -> {
          VideoSettingsFilterPresetsCard()
          VideoSettingsFiltersCard()
          VideoSettingsDebandCard()
        }
        PlayerLiveSettings.SubtitleStyle -> {
          SubtitleSettingsTypographyCard(viewModel)
          SubtitleSettingsColorsCard(viewModel)
          SubtitlesMiscellaneousCard()
        }
      }
    }
  }
}
