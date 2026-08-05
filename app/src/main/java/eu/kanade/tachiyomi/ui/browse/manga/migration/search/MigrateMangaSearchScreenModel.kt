package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.interactor.GetManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaSearchScreenModel(
    val mangaId: Long,
    initialExtensionFilter: String = "",
    getManga: GetManga = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
) : MangaSearchScreenModel() {

    override val migrationSourcePriority: List<Long> =
        sourcePreferences.migrationSourcePriorityManga().get()
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }

    private val excludedSources = sourcePreferences.migrationExcludedSourcesManga().get()
    private val excludedLanguages = sourcePreferences.migrationExcludedLanguagesManga().get()

    init {
        extensionFilter = initialExtensionFilter
        screenModelScope.launch {
            val manga = getManga.await(mangaId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = manga.source,
                    searchQuery = manga.title,
                )
            }

            search()
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        return super.getEnabledSources()
            .filter { "${it.id}" !in excludedSources && it.lang !in excludedLanguages }
            .filter {
                state.value.sourceFilter != MangaSourceFilter.PinnedOnly ||
                    pinnedSources.isEmpty() ||
                    "${it.id}" in pinnedSources
            }
            .sortedWith(
                compareBy(
                    { priorityRank(it.id) },
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
