/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.browser.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import app.gyrolet.mpvrx.presentation.components.AppDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.gyrolet.mpvrx.ui.theme.AppConnectedControlShapeTokens
import app.gyrolet.mpvrx.ui.theme.ConnectedLayout
import app.gyrolet.mpvrx.ui.theme.rememberConnectedShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.MediaServerPreferences
import app.gyrolet.mpvrx.preferences.MusicSourceProvider
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.repository.JellyfinRepository
import app.gyrolet.mpvrx.repository.NavidromeRepository
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.preferences.MediaServersPreferencesScreen
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.navigateTo
import org.koin.compose.koinInject

/**
 * Compact music source selector dropdown located directly next to the TopBar title.
 */
@Composable
fun MusicSourceDropdown(
  modifier: Modifier = Modifier,
) {
  val backStack = LocalBackStack.current
  val mediaServerPreferences = koinInject<MediaServerPreferences>()
  val currentMusicSource by mediaServerPreferences.musicSourceProvider.collectAsState()

  val jellyfinRepository = koinInject<JellyfinRepository>()
  val jellyfinServers by jellyfinRepository.allServers.collectAsState(initial = emptyList())
  val hasJellyfin = jellyfinServers.isNotEmpty()

  val navidromeRepository = koinInject<NavidromeRepository>()
  val navidromeServers by navidromeRepository.allServers.collectAsState(initial = emptyList())
  val hasNavidrome = navidromeServers.isNotEmpty()

  var isDropdownOpen by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .padding(start = 4.dp)
        .clickable { isDropdownOpen = true }
        .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
      when (currentMusicSource) {
        MusicSourceProvider.JELLYFIN -> {
          androidx.compose.material3.Icon(
            painter = painterResource(R.drawable.ic_jellyfin),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        MusicSourceProvider.NAVIDROME -> {
          androidx.compose.material3.Icon(
            painter = painterResource(R.drawable.ic_navidrome),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        MusicSourceProvider.AUDIOBOOKS -> {
          Icon(
            Icons.RoundedFilled.Audiobookshelf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
          )
        }
        else -> {
          // Local music
        }
      }

      Icon(
        Icons.RoundedFilled.ArrowDropDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp),
      )
    }


    val sourceCount = 2 + (if (hasJellyfin) 1 else 0) + (if (hasNavidrome) 1 else 0)
    DropdownMenu(
      expanded = isDropdownOpen,
      onDismissRequest = { isDropdownOpen = false },
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(AppConnectedControlShapeTokens.spacing),
      ) {
        MusicSourceOption(
          label = stringResource(R.string.ui_music),
          selected = currentMusicSource == MusicSourceProvider.LOCAL,
          index = 0,
          itemCount = sourceCount,
          leadingIcon = {
            Icon(Icons.RoundedFilled.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
          },
          onClick = {
            mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.LOCAL)
            isDropdownOpen = false
          },
        )
        if (hasJellyfin) {
          MusicSourceOption(
            label = stringResource(R.string.pref_jellyfin_title),
            selected = currentMusicSource == MusicSourceProvider.JELLYFIN,
            index = 1,
            itemCount = sourceCount,
            leadingIcon = {
              androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_jellyfin),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
            },
            onClick = {
              mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.JELLYFIN)
              isDropdownOpen = false
            },
          )
        }
        if (hasNavidrome) {
          MusicSourceOption(
            label = stringResource(R.string.music_source_navidrome),
            selected = currentMusicSource == MusicSourceProvider.NAVIDROME,
            index = 1 + (if (hasJellyfin) 1 else 0),
            itemCount = sourceCount,
            leadingIcon = {
              androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_navidrome),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
            },
            onClick = {
              mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.NAVIDROME)
              isDropdownOpen = false
            },
          )
        }
        MusicSourceOption(
          label = stringResource(R.string.audiobooks_title),
          selected = currentMusicSource == MusicSourceProvider.AUDIOBOOKS,
          index = sourceCount - 1,
          itemCount = sourceCount,
          leadingIcon = {
            Icon(
              Icons.RoundedFilled.Audiobookshelf,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
          },
          onClick = {
            mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.AUDIOBOOKS)
            isDropdownOpen = false
          },
        )
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 4.dp),
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
        MusicSourceOption(
          label = stringResource(R.string.pref_media_servers_title),
          selected = false,
          index = 0,
          itemCount = 1,
          leadingIcon = {
            Icon(Icons.RoundedFilled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
          },
          onClick = {
            isDropdownOpen = false
            backStack.navigateTo(MediaServersPreferencesScreen)
          },
        )
      }
    }
  }
}

/** Dense, accessible and dynamically connected row for the current menu contents. */
@Composable
private fun MusicSourceOption(
  label: String,
  selected: Boolean,
  index: Int,
  itemCount: Int,
  leadingIcon: @Composable () -> Unit,
  onClick: () -> Unit,
) {
  val shape = rememberConnectedShape(
    index = index,
    itemCount = itemCount,
    layout = ConnectedLayout.Vertical,
    tokens = AppConnectedControlShapeTokens,
  )
  Surface(
    onClick = onClick,
    shape = shape,
    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
      modifier = Modifier
        .widthIn(min = 216.dp, max = 288.dp)
        .heightIn(min = 48.dp)
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingIcon() }
      Text(
        text = label,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (selected) {
        Icon(
          Icons.RoundedFilled.Check,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}
