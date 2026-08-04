package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.interactor.GetAnime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAnimeSearchScreenModel(
    val animeId: Long,
    initialExtensionFilter: String = "",
    getAnime: GetAnime = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
) : AnimeSearchScreenModel() {

    override val migrationSourcePriority: List<Long> =
        sourcePreferences.migrationSourcePriorityAnime().get()
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }

    init {
        extensionFilter = initialExtensionFilter
        screenModelScope.launch {
            val anime = getAnime.await(animeId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = anime.source,
                    searchQuery = anime.title,
                )
            }

            search()
        }
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        return super.getEnabledSources()
            .filter {
                state.value.sourceFilter != AnimeSourceFilter.PinnedOnly ||
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
