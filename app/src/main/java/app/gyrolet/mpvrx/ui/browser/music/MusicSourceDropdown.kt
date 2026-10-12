/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.browser.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    DropdownMenu(
      expanded = isDropdownOpen,
      onDismissRequest = { isDropdownOpen = false },
    ) {
      // Local Music
      DropdownMenuItem(
        text = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(R.string.ui_music))
            if (currentMusicSource == MusicSourceProvider.LOCAL) {
              Spacer(Modifier.width(12.dp))
              Icon(
                Icons.RoundedFilled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        },
        leadingIcon = {
          Icon(Icons.RoundedFilled.Folder, contentDescription = null)
        },
        onClick = {
          mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.LOCAL)
          isDropdownOpen = false
        },
      )

      // Jellyfin
      if (hasJellyfin) {
        DropdownMenuItem(
          text = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(stringResource(R.string.pref_jellyfin_title))
              if (currentMusicSource == MusicSourceProvider.JELLYFIN) {
                Spacer(Modifier.width(12.dp))
                Icon(
                  Icons.RoundedFilled.Check,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          },
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

      // Navidrome
      if (hasNavidrome) {
        DropdownMenuItem(
          text = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(stringResource(R.string.music_source_navidrome))
              if (currentMusicSource == MusicSourceProvider.NAVIDROME) {
                Spacer(Modifier.width(12.dp))
                Icon(
                  Icons.RoundedFilled.Check,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          },
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

      // Audiobooks
      DropdownMenuItem(
        text = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(R.string.audiobooks_title))
            if (currentMusicSource == MusicSourceProvider.AUDIOBOOKS) {
              Spacer(Modifier.width(12.dp))
              Icon(
                Icons.RoundedFilled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        },
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

      HorizontalDivider()

      DropdownMenuItem(
        text = { Text(stringResource(R.string.pref_media_servers_title)) },
        leadingIcon = {
          Icon(Icons.RoundedFilled.Settings, contentDescription = null)
        },
        onClick = {
          isDropdownOpen = false
          backStack.navigateTo(MediaServersPreferencesScreen)
        },
      )
    }
  }
}
