package eu.kanade.tachiyomi.ui.browse.anime.migration.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAnimeScreenModel(
    private val sourceId: Long,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val getFavorites: GetAnimeFavorites = Injekt.get(),
) : StateScreenModel<MigrateAnimeScreenModel.State>(State()) {

    private val _events: Channel<MigrationAnimeEvent> = Channel()
    val events: Flow<MigrationAnimeEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            mutableState.update { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(MigrationAnimeEvent.FailedFetchingFavorites)
                    mutableState.update { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                .map { anime ->
                    anime
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toImmutableList()
                }
                .collectLatest { list ->
                    mutableState.update { state ->
                        // Drop any selection that no longer exists (e.g. after a migration)
                        val ids = list.mapTo(HashSet()) { it.id }
                        state.copy(
                            titleList = list,
                            selection = state.selection.filter { it in ids },
                        )
                    }
                }
        }
    }

    fun toggleSelection(anime: Anime) {
        mutableState.update { state ->
            val newSelection = if (anime.id in state.selection) {
                state.selection - anime.id
            } else {
                state.selection + anime.id
            }
            state.copy(selection = newSelection)
        }
    }

    fun toggleAllSelection() {
        mutableState.update { state ->
            val allSelected = state.selection.size == state.titles.size && state.titles.isNotEmpty()
            state.copy(selection = if (allSelected) emptyList() else state.titles.map { it.id })
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptyList()) }
    }

    @Immutable
    data class State(
        val source: AnimeSource? = null,
        private val titleList: ImmutableList<Anime>? = null,
        val selection: List<Long> = emptyList(),
    ) {

        val titles: ImmutableList<Anime>
            get() = titleList ?: persistentListOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()

        val selectionMode: Boolean
            get() = selection.isNotEmpty()
    }
}

sealed interface MigrationAnimeEvent {
    data object FailedFetchingFavorites : MigrationAnimeEvent
}
