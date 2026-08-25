package eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.reader.loader.NovelSourceCompat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalMangaSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
    // When true, search only novel sources; when false, only non-novel (manga) sources.
    private val novelOnly: Boolean = false,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : MangaSearchScreenModel(
    State(
        searchQuery = initialQuery,
        // Searching within a specific extension forces All (so its sources aren't excluded by the
        // pin filter); otherwise open with the user's remembered Pinned/All choice.
        sourceFilter = when {
            !initialExtensionFilter.isNullOrBlank() -> MangaSourceFilter.All
            sourcePreferences.globalSearchPinnedOnly().get() -> MangaSourceFilter.PinnedOnly
            else -> MangaSourceFilter.All
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
    override fun setSourceFilter(filter: MangaSourceFilter) {
        sourcePreferences.globalSearchPinnedOnly().set(filter == MangaSourceFilter.PinnedOnly)
        super.setSourceFilter(filter)
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        return super.getEnabledSources()
            .filter { NovelSourceCompat.isNovelSource(it.id) == novelOnly }
            .filter { state.value.sourceFilter != MangaSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
