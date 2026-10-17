package app.gyrolet.mpvrx.ui.background

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.*
import org.koin.compose.koinInject

/**
 * Full-screen picker for the background animation. A choice applies at once and plays behind the
 * controls, so the page stays open to try the next one.
 *
 * Layout: title and caption, a three-column grid of tiles, the interval row while Random is
 * picked, a divider, then the Parallax, Show-over-wallpaper and Seasonal-themes switches and Close.
 */
@Composable
fun BackgroundPickerDialog(onDismiss: () -> Unit) {
  val prefs = koinInject<AppearancePreferences>()
  val picked by prefs.backgroundType.collectAsState()
  val interval by prefs.randomBackgroundInterval.collectAsState()
  val parallax by prefs.backgroundParallax.collectAsState()
  val overWallpaper by prefs.backgroundOverWallpaper.collectAsState()
  val seasonal by prefs.seasonalThemes.collectAsState()
  val active = rememberActiveBackground()

  // The page behind stops animating while this one covers it
  DisposableEffect(Unit) {
    BackdropGate.covers++
    onDispose { BackdropGate.covers-- }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      AnimatedBackground(type = active.type, parallax = parallax)

      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(Modifier.height(24.dp))
        Text(
          text = stringResource(R.string.bg_picker_title),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = stringResource(R.string.bg_picker_description),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        // A running event shows its own backdrop over the pick; say why the pick is not what plays
        if (active.event != null && active.type.isSeasonal && picked != BackgroundType.NONE) {
          Text(
            text = stringResource(R.string.bg_seasonal_notice, stringResource(active.type.labelRes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
          )
        }

        TileGrid(
          items = BackgroundType.PICKABLE,
          columns = 3,
        ) { type, modifier ->
          Tile(
            selected = picked == type,
            icon = iconFor(type),
            label = stringResource(type.labelRes),
            modifier = modifier,
            onClick = {
              prefs.backgroundType.set(type)
              if (type == BackgroundType.RANDOM) RandomBackground.reroll(prefs)
            },
          )
        }

        if (picked == BackgroundType.RANDOM) {
          Spacer(Modifier.height(12.dp))
          TileGrid(items = RandomInterval.entries, columns = RandomInterval.entries.size) { item, modifier ->
            Tile(
              selected = interval == item,
              icon = iconFor(item),
              label = stringResource(item.labelRes),
              modifier = modifier,
              onClick = {
                prefs.randomBackgroundInterval.set(item)
                RandomBackground.reroll(prefs)
              },
            )
          }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))

        if (picked != BackgroundType.NONE) {
          SwitchCard(
            icon = MaterialSymbols.RoundedFilled.Screen_rotation,
            title = stringResource(R.string.bg_parallax_title),
            summary = stringResource(R.string.bg_parallax_summary),
            checked = parallax,
            onChange = { prefs.backgroundParallax.set(it) },
          )
          Spacer(Modifier.height(12.dp))
          SwitchCard(
            icon = MaterialSymbols.RoundedFilled.Image,
            title = stringResource(R.string.bg_over_wallpaper_title),
            summary = stringResource(R.string.bg_over_wallpaper_summary),
            checked = overWallpaper,
            onChange = { prefs.backgroundOverWallpaper.set(it) },
          )
          Spacer(Modifier.height(12.dp))
        }
        SwitchCard(
          icon = MaterialSymbols.RoundedFilled.Celebration,
          title = stringResource(R.string.bg_seasonal_title),
          summary = stringResource(R.string.bg_seasonal_summary),
          checked = seasonal,
          onChange = { prefs.seasonalThemes.set(it) },
        )

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
          Text(stringResource(R.string.bg_close), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
      }
    }
  }
}

/** Lays [items] out in rows of [columns] equal tiles; a short last row is padded with empty space. */
@Composable
private fun <T> TileGrid(
  items: List<T>,
  columns: Int,
  tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
    items.chunked(columns).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { item -> tile(item, Modifier.weight(1f)) }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun Tile(
  selected: Boolean,
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cs = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    modifier = modifier.height(76.dp),
    shape = RoundedCornerShape(18.dp),
    color = if (selected) cs.primaryContainer.copy(alpha = 0.55f) else cs.surfaceContainer.copy(alpha = 0.82f),
    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(icon, contentDescription = null, tint = if (selected) cs.primary else cs.primary.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
      Spacer(Modifier.height(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) cs.onPrimaryContainer else cs.onSurface,
        maxLines = 1,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      )
    }
  }
}

@Composable
private fun SwitchCard(
  icon: ImageVector,
  title: String,
  summary: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  Surface(
    onClick = { onChange(!checked) },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    color = cs.surfaceContainer.copy(alpha = 0.82f),
    border = BorderStroke(1.dp, cs.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(30.dp))
      Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
      }
      Switch(checked = checked, onCheckedChange = onChange)
    }
  }
}

private fun iconFor(type: BackgroundType): ImageVector =
  when (type) {
    BackgroundType.CIRCLES -> MaterialSymbols.RoundedFilled.Circle
    BackgroundType.RINGS -> MaterialSymbols.RoundedFilled.Radio_button_unchecked
    BackgroundType.SPACE -> MaterialSymbols.RoundedFilled.Auto_awesome
    BackgroundType.SHAPES -> MaterialSymbols.RoundedFilled.Pentagon
    BackgroundType.SNOW -> MaterialSymbols.RoundedFilled.Ac_unit
    BackgroundType.GRID -> MaterialSymbols.RoundedFilled.Grid_view
    BackgroundType.PARTICLES -> MaterialSymbols.RoundedFilled.Grain
    BackgroundType.LAVA -> MaterialSymbols.RoundedFilled.Water_drop
    BackgroundType.AURORA -> MaterialSymbols.RoundedFilled.Gradient
    BackgroundType.FIREFLIES -> MaterialSymbols.RoundedFilled.Flare
    BackgroundType.RAIN -> MaterialSymbols.RoundedFilled.Rainy
    BackgroundType.BOKEH -> MaterialSymbols.RoundedFilled.Lens_blur
    BackgroundType.SAKURA -> MaterialSymbols.RoundedFilled.Local_florist
    BackgroundType.CONSTELLATION -> MaterialSymbols.RoundedFilled.Hub
    BackgroundType.BUBBLES -> MaterialSymbols.RoundedFilled.Bubble_chart
    BackgroundType.NONE -> MaterialSymbols.RoundedFilled.Visibility_off
    BackgroundType.RANDOM -> MaterialSymbols.RoundedFilled.Shuffle
    // Seasonal types are never tiles, but the when must be exhaustive
    else -> MaterialSymbols.RoundedFilled.Celebration
  }

private fun iconFor(interval: RandomInterval): ImageVector =
  when (interval) {
    RandomInterval.EACH_LAUNCH -> MaterialSymbols.RoundedFilled.Play_circle
    RandomInterval.EVERY_DAY -> MaterialSymbols.RoundedFilled.Calendar_today
    RandomInterval.EVERY_3_DAYS -> MaterialSymbols.RoundedFilled.Schedule
  }
