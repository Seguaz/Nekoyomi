package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.i18n.stringResource

/**
 * A "Tags" section for the library filter sheet: one tri-state chip per tag present in the library.
 * Tapping a chip cycles it neutral -> included (check) -> excluded (red cross) -> neutral. A trailing
 * "Reset" chip appears while any tag is selected. Renders nothing when the library has no tags.
 */
@Composable
fun GenreFilterSection(
    genres: ImmutableList<String>,
    includedGenres: Set<String>,
    excludedGenres: Set<String>,
    onGenreClick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (genres.isEmpty()) return
    SettingsChipRow(MR.strings.library_filter_tags) {
        genres.forEach { genre ->
            val included = genre in includedGenres
            val excluded = genre in excludedGenres
            val leadingIcon: (@Composable () -> Unit)? = when {
                included -> {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                }
                excluded -> {
                    {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                }
                else -> null
            }
            FilterChip(
                selected = included || excluded,
                onClick = { onGenreClick(genre) },
                label = { Text(genre) },
                leadingIcon = leadingIcon,
                colors = if (excluded) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
        if (includedGenres.isNotEmpty() || excludedGenres.isNotEmpty()) {
            AssistChip(
                onClick = onClear,
                label = { Text(stringResource(MR.strings.action_reset)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
