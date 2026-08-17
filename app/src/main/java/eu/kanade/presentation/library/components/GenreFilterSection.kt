package eu.kanade.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.header

/**
 * A collapsible "Tags" section for the library filter sheet: one tri-state chip per tag present in
 * the library. Tapping a chip cycles it neutral -> included (check) -> excluded (red cross) -> neutral.
 * A trailing "Reset" chip appears while any tag is selected. The section is collapsible (and collapsed
 * by default) so the long tag list stays out of the way for users who don't filter by tag; the
 * collapsed header shows how many tags are active. Renders nothing when the library has no tags.
 */
@Composable
fun GenreFilterSection(
    genres: ImmutableList<String>,
    includedGenres: Set<String>,
    excludedGenres: Set<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onGenreClick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (genres.isEmpty()) return
    val activeCount = includedGenres.size + excludedGenres.size
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.library_filter_tags),
                style = MaterialTheme.typography.header,
            )
            if (activeCount > 0) {
                Text(
                    text = " ($activeCount)",
                    style = MaterialTheme.typography.header,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        AnimatedVisibility(visible = expanded) {
            FlowRow(
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
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
                if (activeCount > 0) {
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
    }
}
