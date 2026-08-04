package eu.kanade.tachiyomi.ui.browse.manga.migration.advanced

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.manga.interactor.MigrateManga
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaListScreenModel(
    private val mangaIds: List<Long>,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val migrateManga: MigrateManga = MigrateManga(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateMangaListScreenModel.State>(State()) {

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledMangaSources().get()
    private val pinnedSources = sourcePreferences.pinnedMangaSources().get()
    private var migrationPriority = parsePriority(sourcePreferences.migrationSourcePriorityManga().get())

    private fun parsePriority(raw: String): List<Long> =
        raw.split(",").mapNotNull { it.trim().toLongOrNull() }

    private fun priorityRank(id: Long): Int =
        migrationPriority.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    init {
        // Receive manually picked alternatives from the search screen.
        screenModelScope.launch {
            MangaMigrationSelectionBus.selections.collect { (oldMangaId, newManga) ->
                onManualSelection(oldMangaId, newManga)
            }
        }
        screenModelScope.launchIO { start() }
        // Re-run the suggestions when the migration priority order is edited (e.g. from the toolbar).
        screenModelScope.launch {
            sourcePreferences.migrationSourcePriorityManga().changes().collectLatest { raw ->
                val updated = parsePriority(raw)
                if (updated != migrationPriority) {
                    migrationPriority = updated
                    start()
                }
            }
        }
    }

    private suspend fun start() {
        val mangas = mangaIds.mapNotNull { getManga.await(it) }
        val items = mangas.map { manga ->
            MigratingMangaItem(
                oldManga = manga,
                oldSourceName = sourceManager.getOrStub(manga.source).name,
                result = SearchResult.Searching,
            )
        }
        mutableState.update {
            it.copy(items = items, isLoading = false, isSearching = true)
        }

        val semaphore = Semaphore(SEARCH_CONCURRENCY)
        coroutineScope {
            items.map { item ->
                async {
                    semaphore.withPermit {
                        val result = findBestMatch(item.oldManga)
                        updateItemResult(item.oldManga.id, result)
                    }
                }
            }.awaitAll()
        }

        mutableState.update { it.copy(isSearching = false) }
    }

    private fun candidateSources(fromSourceId: Long): List<CatalogueSource> {
        val enabled = sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources && it.id != fromSourceId }
            .sortedWith(
                compareBy(
                    { priorityRank(it.id) },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
        // User-defined priority sources are always searched first, in their order (even if unpinned).
        val prioritized = enabled.filter { priorityRank(it.id) != Int.MAX_VALUE }
        // Then default to pinned sources (matching the single-item migrate default); fall back to all.
        val pinned = enabled.filter { "${it.id}" in pinnedSources }
        return (prioritized + pinned.ifEmpty { enabled }).distinctBy { it.id }
    }

    private suspend fun findBestMatch(oldManga: Manga): SearchResult {
        val query = oldManga.title
        val normalizedQuery = query.normalizedForMatch()
        var fallback: Pair<CatalogueSource, Manga>? = null

        for (source in candidateSources(oldManga.source)) {
            val results = try {
                source.getSearchManga(1, query, source.getFilterList()).mangas
            } catch (_: Throwable) {
                continue
            }
            if (results.isEmpty()) continue

            val exact = results.firstOrNull { it.title.normalizedForMatch() == normalizedQuery }
            if (exact != null) {
                val localManga = networkToLocalManga.await(exact.toDomainManga(source.id))
                return SearchResult.Found(localManga, source.name, fetchChapterCount(source, localManga))
            }
            if (fallback == null) {
                val localManga = networkToLocalManga.await(results.first().toDomainManga(source.id))
                fallback = source to localManga
            }
        }

        return fallback?.let { (source, manga) ->
            SearchResult.Found(manga, source.name, fetchChapterCount(source, manga))
        } ?: SearchResult.NotFound
    }

    private suspend fun fetchChapterCount(source: CatalogueSource, manga: Manga): Int {
        return try {
            source.getMangaUpdate(
                manga.toSManga(),
                chapters = emptyList(),
                fetchDetails = false,
                fetchChapters = true,
            ).chapters.size
        } catch (_: Throwable) {
            0
        }
    }

    private suspend fun onManualSelection(oldMangaId: Long, newManga: Manga) {
        if (state.value.items.none { it.oldManga.id == oldMangaId }) return
        updateItemResult(oldMangaId, SearchResult.Searching)
        val source = sourceManager.get(newManga.source) as? CatalogueSource
        val chapterCount = source?.let { fetchChapterCount(it, newManga) } ?: 0
        updateItemResult(
            oldMangaId,
            SearchResult.Found(newManga, source?.name ?: "", chapterCount),
        )
    }

    private fun updateItemResult(oldMangaId: Long, result: SearchResult) {
        mutableState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.oldManga.id == oldMangaId) item.copy(result = result) else item
                },
            )
        }
    }

    fun removeManga(oldMangaId: Long) {
        mutableState.update { state ->
            state.copy(items = state.items.filterNot { it.oldManga.id == oldMangaId })
        }
    }

    fun openMigrateDialog() {
        mutableState.update { it.copy(dialog = Dialog.Migrate) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun startMigration(replace: Boolean, flags: Int) {
        migrateFlags.set(flags)
        screenModelScope.launchIO {
            val toMigrate = state.value.items.mapNotNull { item ->
                (item.result as? SearchResult.Found)?.let { item.oldManga to it.newManga }
            }
            mutableState.update {
                it.copy(
                    dialog = null,
                    isMigrating = true,
                    migrationTotal = toMigrate.size,
                    migratedCount = 0,
                )
            }
            toMigrate.forEach { (oldManga, newManga) ->
                migrateManga.await(oldManga, newManga, replace, flags)
                mutableState.update { it.copy(migratedCount = it.migratedCount + 1) }
            }
            mutableState.update { it.copy(isMigrating = false, finished = true) }
        }
    }

    @Immutable
    data class State(
        val items: List<MigratingMangaItem> = emptyList(),
        val isLoading: Boolean = true,
        val isSearching: Boolean = false,
        val isMigrating: Boolean = false,
        val migratedCount: Int = 0,
        val migrationTotal: Int = 0,
        val finished: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        val foundCount: Int = items.count { it.result is SearchResult.Found }
        val searchProgress: Int = items.count { it.result !is SearchResult.Searching }
        val canMigrate: Boolean = !isSearching && !isMigrating && foundCount > 0
    }

    sealed interface Dialog {
        data object Migrate : Dialog
    }

    companion object {
        private const val SEARCH_CONCURRENCY = 5
    }
}

@Immutable
data class MigratingMangaItem(
    val oldManga: Manga,
    val oldSourceName: String,
    val result: SearchResult,
)

sealed interface SearchResult {
    data object Searching : SearchResult
    data object NotFound : SearchResult
    data class Found(
        val newManga: Manga,
        val sourceName: String,
        val chapterCount: Int,
    ) : SearchResult
}

private fun String.normalizedForMatch(): String {
    // Fold accents, treat the multiplication sign as a plain "x" (e.g. SPY×FAMILY == Spy x Family),
    // then keep only ascii letters/digits so punctuation/spacing/casing don't break exact matching.
    return java.text.Normalizer.normalize(trim(), java.text.Normalizer.Form.NFD)
        .lowercase()
        .replace('×', 'x')
        .replace(Regex("[^a-z0-9]"), "")
}
