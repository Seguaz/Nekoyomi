package eu.kanade.tachiyomi.ui.browse.anime.migration.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.MigrateAnimeSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreenModel
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
                // Drilling into a full source browse isn't supported in select mode; results shown
                // in the global search rows cover the common case and the query can be refined.
            },
            onClickItem = { anime ->
                AnimeMigrationSelectionBus.select(oldAnimeId, anime)
                navigator.pop()
            },
            onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
        )
    }
}
