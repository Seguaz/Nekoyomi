package eu.kanade.presentation.browse.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.browse.manga.migration.manga.MigrateMangaScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateMangaScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateMangaScreenModel.State,
    onClickItem: (Manga) -> Unit,
    onSelectItem: (Manga) -> Unit,
    onSelectAll: () -> Unit,
    onClickMigrate: () -> Unit,
    onClickCancelSelection: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
                actionModeCounter = state.selection.size,
                onCancelActionMode = onClickCancelSelection,
                actionModeActions = {
                    AppBarActions(
                        actions = persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_all),
                                icon = Icons.Outlined.SelectAll,
                                onClick = onSelectAll,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.migrate),
                                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                onClick = onClickMigrate,
                            ),
                        ),
                    )
                },
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        FastScrollLazyColumn(
            contentPadding = contentPadding,
        ) {
            items(state.titles) { manga ->
                MigrateMangaItem(
                    manga = manga,
                    selected = manga.id in state.selection,
                    onClick = {
                        if (state.selectionMode) onSelectItem(manga) else onClickItem(manga)
                    },
                    onLongClick = { onSelectItem(manga) },
                )
            }
        }
    }
}

@Composable
private fun MigrateMangaItem(
    manga: Manga,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .height(76.dp)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Book(
            data = manga,
            modifier = Modifier.fillMaxHeight(),
        )
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = MaterialTheme.padding.small),
            )
        }
    }
}
