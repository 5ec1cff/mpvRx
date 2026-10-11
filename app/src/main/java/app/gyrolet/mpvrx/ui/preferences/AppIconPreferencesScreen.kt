/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.theme.AppConnectedShapeTokens
import app.gyrolet.mpvrx.ui.theme.ConnectedLayout
import app.gyrolet.mpvrx.ui.theme.rememberConnectedShape
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable

/** User-selectable built-in launcher icons; new designs only require catalogue + manifest entries. */
@Serializable
object AppIconPreferencesScreen : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    var current by remember { mutableStateOf(LauncherIconManager.current(context)) }
    Scaffold(
      topBar = {
        SettingsTopAppBar(
          title = stringResource(R.string.pref_app_icon_title),
          onBack = { backstack.popSafely() },
        )
      },
    ) { padding ->
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
          start = 16.dp, end = 16.dp,
          top = padding.calculateTopPadding() + 12.dp,
          bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(AppConnectedShapeTokens.spacing),
      ) {
        item {
          Text(
            text = stringResource(R.string.pref_app_icon_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
          )
        }
        itemsIndexed(LauncherIcon.entries, key = { _, choice -> choice.name }) { index, choice ->
          val selected = current == choice
          val shape = rememberConnectedShape(index, LauncherIcon.entries.size, ConnectedLayout.Vertical)
          val borderColor by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            animationSpec = AppMotion.spatial(AppMotion.Effect.Color, snap()),
            label = "appIconSelection",
          )
          Card(
            onClick = {
              if (!selected) {
                try {
                  LauncherIconManager.select(context, choice)
                  current = LauncherIconManager.current(context)
                } catch (_: Exception) {
                  Toast.makeText(context, R.string.app_icon_change_failed, Toast.LENGTH_LONG).show()
                  current = LauncherIconManager.current(context)
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(
              if (selected) 2.dp else 1.dp,
              borderColor,
            ),
            shape = shape,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Box(
                modifier = Modifier.size(86.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
              ) {
                // Use Android's drawable renderer for native VectorDrawable gradient support.
                AndroidView(
                  modifier = Modifier.fillMaxSize(),
                  factory = { viewContext ->
                    ImageView(viewContext).apply {
                      scaleType = ImageView.ScaleType.FIT_CENTER
                      setImageResource(choice.preview)
                      contentDescription = viewContext.getString(choice.label)
                    }
                  },
                  update = {
                    it.setImageResource(choice.preview)
                    it.contentDescription = context.getString(choice.label)
                  },
                )
              }
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(stringResource(choice.label), style = MaterialTheme.typography.titleMedium)
                Text(
                  stringResource(choice.description),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              RadioButton(selected = selected, onClick = null)
            }
          }
        }
      }
    }
  }
}
