package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.browse.anime.migration.AnimeMigrationFlags
import eu.kanade.tachiyomi.ui.browse.anime.migration.advanced.MigrateAnimeListScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.migration.advanced.MigratingAnimeItem
import eu.kanade.tachiyomi.ui.browse.anime.migration.advanced.SearchResult
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun MigrateAnimeListScreenContent(
    state: MigrateAnimeListScreenModel.State,
    migrateFlags: () -> Int,
    navigateUp: () -> Unit,
    onClickItem: (SearchResult.Found) -> Unit,
    onChangeMatch: (MigratingAnimeItem) -> Unit,
    onRemoveItem: (MigratingAnimeItem) -> Unit,
    onClickMigrate: () -> Unit,
    onDismissDialog: () -> Unit,
    onConfirmMigrate: (replace: Boolean, flags: Int) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.migrate),
                subtitle = if (state.isSearching) {
                    stringResource(AYMR.strings.migration_searching)
                } else {
                    "${state.foundCount}/${state.items.size}"
                },
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.canMigrate) {
                ExtendedFloatingActionButton(
                    text = {
                        Text(text = "${stringResource(AYMR.strings.migration_migrate_all)} (${state.foundCount})")
                    },
                    icon = {
                        Icon(imageVector = Icons.Outlined.ArrowDownward, contentDescription = null)
                    },
                    onClick = onClickMigrate,
                )
            }
        },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen(modifier = Modifier.padding(contentPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(
                items = state.items,
                key = { it.oldAnime.id },
            ) { item ->
                MigrationCard(
                    item = item,
                    onClickResult = onClickItem,
                    onChangeMatch = { onChangeMatch(item) },
                    onRemove = { onRemoveItem(item) },
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
                )
            }
        }
    }

    if (state.dialog is MigrateAnimeListScreenModel.Dialog.Migrate) {
        MigrateFlagsDialog(
            initialFlags = migrateFlags(),
            onDismissRequest = onDismissDialog,
            onConfirm = onConfirmMigrate,
        )
    }

    if (state.isMigrating) {
        MigratingOverlay(migrated = state.migratedCount, total = state.migrationTotal)
    }
}

@Composable
private fun MigrationCard(
    item: MigratingAnimeItem,
    onClickResult: (SearchResult.Found) -> Unit,
    onChangeMatch: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntryRow(
                    cover = item.oldAnime,
                    title = item.oldAnime.title,
                    subtitle = item.oldSourceName,
                    modifier = Modifier
                        .weight(1f)
                        .secondaryItemAlpha(),
                )
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = MaterialTheme.padding.extraSmall)
                            .clickable { menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(AYMR.strings.migration_pick_alternative)) },
                            onClick = {
                                menuExpanded = false
                                onChangeMatch()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(AYMR.strings.migration_skip)) },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Outlined.ArrowDownward,
                contentDescription = null,
                modifier = Modifier
                    .padding(vertical = MaterialTheme.padding.extraSmall)
                    .align(Alignment.CenterHorizontally)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            when (val result = item.result) {
                SearchResult.Searching -> {
                    Row(
                        modifier = Modifier.height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
                        Text(
                            text = stringResource(AYMR.strings.migration_searching),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                SearchResult.NotFound -> {
                    Row(
                        modifier = Modifier.height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
                        Text(
                            text = stringResource(AYMR.strings.migration_no_alternatives),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is SearchResult.Found -> {
                    EntryRow(
                        cover = result.newAnime,
                        title = result.newAnime.title,
                        subtitle = "${result.sourceName} · " +
                            stringResource(AYMR.strings.migration_episode_count, result.episodeCount),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClickResult(result) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    cover: Any,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Book(
            data = cover,
            modifier = Modifier.height(56.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MigrateFlagsDialog(
    initialFlags: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (replace: Boolean, flags: Int) -> Unit,
) {
    val flags = remember { AnimeMigrationFlags.getAllFlags(initialFlags) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.migration_dialog_what_to_include)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                flags.forEachIndexed { index, flag ->
                    LabeledCheckbox(
                        label = stringResource(flag.titleId),
                        checked = selectedFlags[index],
                        onCheckedChange = { selectedFlags[index] = it },
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onConfirm(false, AnimeMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags))
                    },
                ) {
                    Text(text = stringResource(MR.strings.copy))
                }
                TextButton(
                    onClick = {
                        onConfirm(true, AnimeMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags))
                    },
                ) {
                    Text(text = stringResource(MR.strings.migrate))
                }
            }
        },
    )
}

@Composable
private fun MigratingOverlay(migrated: Int, total: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow all touches while migrating
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
            Text(
                text = "$migrated / $total",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}
