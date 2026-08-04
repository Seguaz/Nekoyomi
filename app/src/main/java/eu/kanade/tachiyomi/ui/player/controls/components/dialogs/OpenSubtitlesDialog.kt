package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.utils.subtitle.OpenSubtitlesUiState
import eu.kanade.tachiyomi.ui.player.utils.subtitle.RemoteSubtitle
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun OpenSubtitlesDialog(
    state: OpenSubtitlesUiState?,
    onSelect: (RemoteSubtitle) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (state == null) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "OpenSubtitles") },
        text = {
            when (state) {
                OpenSubtitlesUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(text = stringResource(AYMR.strings.player_opensubtitles_searching))
                    }
                }
                OpenSubtitlesUiState.Empty -> {
                    Text(text = stringResource(AYMR.strings.player_opensubtitles_none))
                }
                OpenSubtitlesUiState.Error -> {
                    Text(text = stringResource(AYMR.strings.player_opensubtitles_error))
                }
                is OpenSubtitlesUiState.Results -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                    ) {
                        items(state.subtitles) { subtitle ->
                            SubtitleResultRow(
                                subtitle = subtitle,
                                onClick = { onSelect(subtitle) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun SubtitleResultRow(
    subtitle: RemoteSubtitle,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.small),
    ) {
        Text(
            text = subtitle.fileName.ifBlank { subtitle.release }.ifBlank { subtitle.language },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = buildString {
                append(subtitle.language)
                if (subtitle.downloads > 0) {
                    append("  •  ")
                    append(stringResource(AYMR.strings.player_opensubtitles_downloads, subtitle.downloads))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
