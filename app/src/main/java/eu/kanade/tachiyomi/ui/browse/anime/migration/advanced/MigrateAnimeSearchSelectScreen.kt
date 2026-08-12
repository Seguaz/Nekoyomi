package eu.kanade.tachiyomi.ui.browse.anime.migration.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.MigrateAnimeSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.AnimeMigrateSearchScreenDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.AnimeSourceSearchScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.migration.MigrationSourcePriorityScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen

/**
 * Search screen used from the mass migration list to manually pick an alternative for an entry.
 * Unlike [MigrateAnimeSearchScreen], choosing a result does NOT migrate immediately; it reports
 * the pick back to the list screen through [AnimeMigrationSelectionBus] and pops.
 */
class MigrateAnimeSearchSelectScreen(private val oldAnimeId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateAnimeSearchScreenModel(animeId = oldAnimeId) }
        val state by screenModel.state.collectAsState()

        val dialogScreenModel = rememberScreenModel { AnimeMigrateSearchScreenDialogScreenModel(animeId = oldAnimeId) }
        val dialogState by dialogScreenModel.state.collectAsState()

        MigrateAnimeSearchScreen(
            state = state,
            fromSourceId = state.fromSourceId,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getAnime = { screenModel.getAnime(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                // Open the full browse for that source in "select" mode: picking a result there
                // reports back to the migration list via the bus (see AnimeSourceSearchScreen).
                dialogState.anime?.let { anime ->
                    navigator.push(
                        AnimeSourceSearchScreen(anime, it.id, state.searchQuery, selectMode = true),
                    )
                }
            },
            onClickItem = { anime ->
                AnimeMigrationSelectionBus.select(oldAnimeId, anime)
                navigator.pop()
            },
            onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
            onClickPriority = { navigator.push(MigrationSourcePriorityScreen(isManga = false)) },
        )
    }
}
