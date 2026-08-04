package eu.kanade.tachiyomi.ui.browse.manga.migration.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.manga.MigrateMangaSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.migration.MigrationSourcePriorityScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen

/**
 * Search screen used from the mass migration list to manually pick an alternative for an entry.
 * Unlike [MigrateMangaSearchScreen], choosing a result does NOT migrate immediately; it reports
 * the pick back to the list screen through [MangaMigrationSelectionBus] and pops.
 */
class MigrateMangaSearchSelectScreen(private val oldMangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateMangaSearchScreenModel(mangaId = oldMangaId) }
        val state by screenModel.state.collectAsState()

        MigrateMangaSearchScreen(
            state = state,
            fromSourceId = state.fromSourceId,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getManga = { screenModel.getManga(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                // Drilling into a full source browse isn't supported in select mode; results shown
                // in the global search rows cover the common case and the query can be refined.
            },
            onClickItem = { manga ->
                MangaMigrationSelectionBus.select(oldMangaId, manga)
                navigator.pop()
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
            onClickPriority = { navigator.push(MigrationSourcePriorityScreen(isManga = true)) },
        )
    }
}
