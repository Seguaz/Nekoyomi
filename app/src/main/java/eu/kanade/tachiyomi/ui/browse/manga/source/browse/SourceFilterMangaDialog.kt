package eu.kanade.tachiyomi.ui.browse.manga.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.CollapsibleBox
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SelectItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TextItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SourceFilterMangaDialog(
    onDismissRequest: () -> Unit,
    filters: FilterList,
    onReset: () -> Unit,
    onFilter: () -> Unit,
    onUpdate: (FilterList) -> Unit,
) {
    val updateFilters = { onUpdate(filters) }
    var query by remember { mutableStateOf("") }
    val showSearch = remember(filters) { filters.sumOf { it.leafCount() } >= FILTER_SEARCH_THRESHOLD }
    // When searching, show each matching option under its group's name (a String heading) so the user
    // can tell which category (Genre, Season, …) a result belongs to. Entries are either a String
    // heading or a Filter leaf.
    val displayedEntries: List<Any> = if (query.isBlank()) {
        filters.toList()
    } else {
        buildList {
            filters.forEach { filter ->
                when (filter) {
                    is Filter.Group<*> -> {
                        val matches = filter.state.filterIsInstance<Filter<*>>()
                            .flatMap { it.leafMatches(query) }
                        if (matches.isNotEmpty()) {
                            add(filter.name)
                            addAll(matches)
                        }
                    }
                    is Filter.Header, is Filter.Separator -> {}
                    else -> if (filter.name.contains(query, ignoreCase = true)) add(filter)
                }
            }
        }
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        LazyColumn {
            stickyHeader {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        TextButton(onClick = onReset) {
                            Text(
                                text = stringResource(MR.strings.action_reset),
                                style = LocalTextStyle.current.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(onClick = {
                            onFilter()
                            onDismissRequest()
                        }) {
                            Text(stringResource(MR.strings.action_filter))
                        }
                    }
                    if (showSearch) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                // Align with the filter rows below (SettingsItemsPaddings.Horizontal = 24.dp);
                                // extra bottom gap so it isn't glued to the divider.
                                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
                            placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = null)
                                    }
                                }
                            },
                            singleLine = true,
                        )
                    }
                    HorizontalDivider()
                }
            }

            items(displayedEntries) { entry ->
                when (entry) {
                    is String -> HeadingItem(entry)
                    is Filter<*> -> FilterItem(entry, updateFilters)
                }
            }
        }
    }
}

@Composable
private fun FilterItem(filter: Filter<*>, onUpdate: () -> Unit) {
    when (filter) {
        is Filter.Header -> {
            HeadingItem(filter.name)
        }
        is Filter.Separator -> {
            HorizontalDivider()
        }
        is Filter.CheckBox -> {
            CheckboxItem(
                label = filter.name,
                checked = filter.state,
            ) {
                filter.state = !filter.state
                onUpdate()
            }
        }
        is Filter.TriState -> {
            TriStateItem(
                label = filter.name,
                state = filter.state.toTriStateFilter(),
            ) {
                filter.state = filter.state.toTriStateFilter().next().toTriStateInt()
                onUpdate()
            }
        }
        is Filter.Text -> {
            TextItem(
                label = filter.name,
                value = filter.state,
            ) {
                filter.state = it
                onUpdate()
            }
        }
        is Filter.Select<*> -> {
            SelectItem(
                label = filter.name,
                options = filter.values,
                selectedIndex = filter.state,
                onSelect = {
                    filter.state = it
                    onUpdate()
                },
            )
        }
        is Filter.Sort -> {
            CollapsibleBox(
                heading = filter.name,
            ) {
                Column {
                    filter.values.mapIndexed { index, item ->
                        SortItem(
                            label = item,
                            sortDescending = filter.state?.ascending?.not()
                                ?.takeIf { index == filter.state?.index },
                        ) {
                            val ascending = if (index == filter.state?.index) {
                                !filter.state!!.ascending
                            } else {
                                filter.state!!.ascending
                            }
                            filter.state = Filter.Sort.Selection(
                                index = index,
                                ascending = ascending,
                            )
                            onUpdate()
                        }
                    }
                }
            }
        }
        is Filter.Group<*> -> {
            CollapsibleBox(
                heading = filter.name,
            ) {
                Column {
                    filter.state
                        .filterIsInstance<Filter<*>>()
                        .map { FilterItem(filter = it, onUpdate = onUpdate) }
                }
            }
        }
    }
}

private fun Int.toTriStateFilter(): TriState {
    return when (this) {
        Filter.TriState.STATE_IGNORE -> TriState.DISABLED
        Filter.TriState.STATE_INCLUDE -> TriState.ENABLED_IS
        Filter.TriState.STATE_EXCLUDE -> TriState.ENABLED_NOT
        else -> throw IllegalStateException("Unknown TriState state: $this")
    }
}

private fun TriState.toTriStateInt(): Int {
    return when (this) {
        TriState.DISABLED -> Filter.TriState.STATE_IGNORE
        TriState.ENABLED_IS -> Filter.TriState.STATE_INCLUDE
        TriState.ENABLED_NOT -> Filter.TriState.STATE_EXCLUDE
    }
}

// Only worth showing the filter search once there are enough options to scroll through.
private const val FILTER_SEARCH_THRESHOLD = 8

private fun Filter<*>.leafCount(): Int = when (this) {
    is Filter.Group<*> -> state.filterIsInstance<Filter<*>>().sumOf { it.leafCount() }
    is Filter.Header, is Filter.Separator -> 0
    else -> 1
}

private fun Filter<*>.leafMatches(query: String): List<Filter<*>> = when (this) {
    is Filter.Group<*> -> state.filterIsInstance<Filter<*>>().flatMap { it.leafMatches(query) }
    is Filter.Header, is Filter.Separator -> emptyList()
    else -> if (name.contains(query, ignoreCase = true)) listOf(this) else emptyList()
}
