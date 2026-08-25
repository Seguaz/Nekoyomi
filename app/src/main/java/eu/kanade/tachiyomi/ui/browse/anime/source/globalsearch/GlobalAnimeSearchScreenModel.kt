package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalAnimeSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : AnimeSearchScreenModel(
    State(
        searchQuery = initialQuery,
        // Searching within a specific extension forces All (so its sources aren't excluded by the
        // pin filter); otherwise open with the user's remembered Pinned/All choice.
        sourceFilter = when {
            !initialExtensionFilter.isNullOrBlank() -> AnimeSourceFilter.All
            sourcePreferences.globalSearchPinnedOnly().get() -> AnimeSourceFilter.PinnedOnly
            else -> AnimeSourceFilter.All
        },
    ),
) {

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            search()
        }
    }

    // Remember the user's Pinned/All choice so global search opens with it next time.
    override fun setSourceFilter(filter: AnimeSourceFilter) {
        sourcePreferences.globalSearchPinnedOnly().set(filter == AnimeSourceFilter.PinnedOnly)
        super.setSourceFilter(filter)
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != AnimeSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
