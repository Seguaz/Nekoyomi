package eu.kanade.tachiyomi.ui.browse.anime.migration.advanced

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.MigrateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAnimeListScreenModel(
    private val animeIds: List<Long>,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val migrateAnime: MigrateAnime = MigrateAnime(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateAnimeListScreenModel.State>(State()) {

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledAnimeSources().get()
    private val pinnedSources = sourcePreferences.pinnedAnimeSources().get()
    private val migrationPriority = parsePriority(sourcePreferences.migrationSourcePriorityAnime().get())
    private val excludedSources = sourcePreferences.migrationExcludedSourcesAnime().get()
    private val excludedLanguages = sourcePreferences.migrationExcludedLanguagesAnime().get()

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
            AnimeMigrationSelectionBus.selections.collect { (oldAnimeId, newAnime) ->
                onManualSelection(oldAnimeId, newAnime)
            }
        }
        screenModelScope.launchIO { start() }
    }

    private suspend fun start() {
        val animes = animeIds.mapNotNull { getAnime.await(it) }
        val items = animes.map { anime ->
            MigratingAnimeItem(
                oldAnime = anime,
                oldSourceName = sourceManager.getOrStub(anime.source).name,
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
                        val result = findBestMatch(item.oldAnime)
                        updateItemResult(item.oldAnime.id, result)
                    }
                }
            }.awaitAll()
        }

        mutableState.update { it.copy(isSearching = false) }
    }

    private fun candidateSources(fromSourceId: Long): List<AnimeCatalogueSource> {
        val enabled = sourceManager.getCatalogueSources()
            .filter {
                it.lang in enabledLanguages &&
                    "${it.id}" !in disabledSources &&
                    it.id != fromSourceId &&
                    "${it.id}" !in excludedSources &&
                    it.lang !in excludedLanguages
            }
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

    private suspend fun findBestMatch(oldAnime: Anime): SearchResult {
        val query = oldAnime.title
        val normalizedQuery = query.normalizedForMatch()
        // When the user has defined a priority order, trust it like picking a source by hand: the
        // highest-priority source that returns ANYTHING for this title wins, even if the name differs.
        val trustPriority = migrationPriority.isNotEmpty()
        var fallback: Pair<AnimeCatalogueSource, Anime>? = null

        for (source in candidateSources(oldAnime.source)) {
            val results = try {
                source.getSearchAnime(1, query, source.getFilterList()).animes
            } catch (_: Throwable) {
                continue
            }
            if (results.isEmpty()) continue

            // Within a source, prefer an exact title, then the CLOSEST-titled result (not just its
            // top one), so a prioritized source picks the right entry even when it returns several.
            val exact = results.firstOrNull { it.title.normalizedForMatch() == normalizedQuery }
            val closest = results.maxByOrNull { titleSimilarity(query, it.title) }
            val match = exact ?: closest?.takeIf { titlesMatch(query, it.title) }
            if (match != null || trustPriority) {
                val chosen = match ?: closest ?: results.first()
                val localAnime = networkToLocalAnime.await(chosen.toDomainAnime(source.id))
                return SearchResult.Found(localAnime, source.name, fetchEpisodeCount(source, localAnime))
            }
            if (fallback == null) {
                val localAnime = networkToLocalAnime.await((closest ?: results.first()).toDomainAnime(source.id))
                fallback = source to localAnime
            }
        }

        return fallback?.let { (source, anime) ->
            SearchResult.Found(anime, source.name, fetchEpisodeCount(source, anime))
        } ?: SearchResult.NotFound
    }

    private suspend fun fetchEpisodeCount(source: AnimeCatalogueSource, anime: Anime): Int {
        return try {
            source.getEpisodeList(anime.toSAnime()).size
        } catch (_: Throwable) {
            0
        }
    }

    private suspend fun onManualSelection(oldAnimeId: Long, newAnime: Anime) {
        if (state.value.items.none { it.oldAnime.id == oldAnimeId }) return
        updateItemResult(oldAnimeId, SearchResult.Searching)
        val source = sourceManager.get(newAnime.source) as? AnimeCatalogueSource
        val episodeCount = source?.let { fetchEpisodeCount(it, newAnime) } ?: 0
        updateItemResult(
            oldAnimeId,
            SearchResult.Found(newAnime, source?.name ?: "", episodeCount),
        )
    }

    private fun updateItemResult(oldAnimeId: Long, result: SearchResult) {
        mutableState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.oldAnime.id == oldAnimeId) item.copy(result = result) else item
                },
            )
        }
    }

    fun removeAnime(oldAnimeId: Long) {
        mutableState.update { state ->
            state.copy(items = state.items.filterNot { it.oldAnime.id == oldAnimeId })
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
                (item.result as? SearchResult.Found)?.let { item.oldAnime to it.newAnime }
            }
            mutableState.update {
                it.copy(
                    dialog = null,
                    isMigrating = true,
                    migrationTotal = toMigrate.size,
                    migratedCount = 0,
                )
            }
            toMigrate.forEach { (oldAnime, newAnime) ->
                migrateAnime.await(oldAnime, newAnime, replace, flags)
                mutableState.update { it.copy(migratedCount = it.migratedCount + 1) }
            }
            mutableState.update { it.copy(isMigrating = false, finished = true) }
        }
    }

    @Immutable
    data class State(
        val items: List<MigratingAnimeItem> = emptyList(),
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
data class MigratingAnimeItem(
    val oldAnime: Anime,
    val oldSourceName: String,
    val result: SearchResult,
)

sealed interface SearchResult {
    data object Searching : SearchResult
    data object NotFound : SearchResult
    data class Found(
        val newAnime: Anime,
        val sourceName: String,
        val episodeCount: Int,
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

/**
 * 0..1 title closeness so we can pick the best of a source's results (not just its top one) and
 * decide if a prioritized source has the entry under a slightly different name.
 */
private fun titleSimilarity(query: String, candidate: String): Double {
    val q = query.normalizedForMatch()
    val c = candidate.normalizedForMatch()
    if (q.isEmpty() || c.isEmpty()) return 0.0
    if (q == c) return 1.0
    if (c.contains(q) || q.contains(c)) return 0.9
    val qt = query.matchTokens()
    val ct = candidate.matchTokens()
    if (qt.isEmpty() || ct.isEmpty()) return 0.0
    return qt.intersect(ct).size.toDouble() / qt.union(ct).size
}

private fun titlesMatch(query: String, candidate: String): Boolean =
    titleSimilarity(query, candidate) >= 0.6

private fun String.matchTokens(): Set<String> =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length > 1 }
        .toSet()
