package eu.kanade.tachiyomi.ui.browse.manga.migration.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.MangaSource
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
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaScreenModel(
    private val sourceId: Long,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val getFavorites: GetMangaFavorites = Injekt.get(),
) : StateScreenModel<MigrateMangaScreenModel.State>(State()) {

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            mutableState.update { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(MigrationMangaEvent.FailedFetchingFavorites)
                    mutableState.update { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                .map { manga ->
                    manga
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

    fun toggleSelection(manga: Manga) {
        mutableState.update { state ->
            val newSelection = if (manga.id in state.selection) {
                state.selection - manga.id
            } else {
                state.selection + manga.id
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
        val source: MangaSource? = null,
        private val titleList: ImmutableList<Manga>? = null,
        val selection: List<Long> = emptyList(),
    ) {

        val titles: ImmutableList<Manga>
            get() = titleList ?: persistentListOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()

        val selectionMode: Boolean
            get() = selection.isNotEmpty()
    }
}

sealed interface MigrationMangaEvent {
    data object FailedFetchingFavorites : MigrationMangaEvent
}
