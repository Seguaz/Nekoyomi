package eu.kanade.tachiyomi.ui.library.anime

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.core.util.fastPartition
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.library.LibraryGroupMode
import eu.kanade.tachiyomi.ui.library.SeriesGrouping
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeBackgrounds
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.anime.model.sort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.i18n.MR
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

/**
 * Typealias for the library anime, using the category as keys, and list of anime as values.
 */
typealias AnimeLibraryMap = Map<Category, List<AnimeLibraryItem>>

class AnimeLibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getCategories: GetVisibleAnimeCategories = Injekt.get(),
    private val getTracksPerAnime: GetTracksPerAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val backgroundCache: AnimeBackgroundCache = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val downloadCache: AnimeDownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<AnimeLibraryScreenModel.State>(State()) {

    var activeCategoryIndex: Int by libraryPreferences.lastUsedAnimeCategory().asState(
        screenModelScope,
    )

    private val context = Injekt.get<Application>()
    private val seriesCoverCache = SeriesCoverCache(context, libraryPreferences)

    init {
        screenModelScope.launchIO {
            val searchAndCovers = combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                libraryPreferences.seriesCoversAnime().changes(),
            ) { searchQuery, _ -> searchQuery }
            combine(
                searchAndCovers,
                getLibraryFlow(),
                getTracksPerAnime.subscribe(),
                getTrackingFilterFlow(),
                downloadCache.changes,
            ) { searchQuery, library, tracks, trackingFilter, _ ->
                val sorted = library
                    .applyFilters(tracks, trackingFilter)
                    .applySort(tracks, trackingFilter.keys)
                // Members of each custom series, deduped, feeding the drill-in folder view.
                val folderMembers = sorted.values.asSequence().flatten()
                    .filter { it.seriesName != null && !it.isFolder }
                    .groupBy { it.seriesName!! }
                    .mapValues { (_, members) -> members.distinctBy { it.libraryAnime.anime.id } }
                val display = if (searchQuery != null) {
                    sorted.mapValues { (_, value) -> value.filter { it.matches(searchQuery) } }
                } else {
                    sorted.foldIntoFolders(folderMembers)
                }
                display to folderMembers
            }
                .collectLatest { (display, folderMembers) ->
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            library = display,
                            folderMembers = folderMembers,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueViewingButton().changes(),
            libraryPreferences.libraryGroupModeAnime().changes(),
        ) { categoryTabs, showCount, showContinue, groupMode ->
            mutableState.update { state ->
                state.copy(
                    // Force tabs on when auto-grouping so the generated sections are navigable.
                    showCategoryTabs = categoryTabs || groupMode != 0,
                    showAnimeCount = showCount,
                    showAnimeContinueButton = showContinue,
                )
            }
        }
            .launchIn(screenModelScope)

        combine(
            getAnimelibItemPreferencesFlow(),
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            (
                listOf(
                    prefs.filterDownloaded,
                    prefs.filterUnseen,
                    prefs.filterStarted,
                    prefs.filterBookmarked,
                    prefs.filterCompleted,
                    prefs.filterIntervalCustom,
                ) + trackFilter.values
                ).any { it != TriState.DISABLED }
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(screenModelScope)

        libraryPreferences.pinnedAnimeIds().changes()
            .onEach { pinned -> mutableState.update { it.copy(pinnedIds = pinned) } }
            .launchIn(screenModelScope)

        libraryPreferences.seriesGroupingsAnime().changes()
            .onEach { set -> mutableState.update { it.copy(seriesIds = SeriesGrouping.decode(set).keys) } }
            .launchIn(screenModelScope)
    }

    private suspend fun AnimeLibraryMap.applyFilters(
        trackMap: Map<Long, List<AnimeTrack>>,
        trackingFilter: Map<Long, TriState>,
    ): AnimeLibraryMap {
        val prefs = getAnimelibItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val skipOutsideReleasePeriod = prefs.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnseen = prefs.filterUnseen
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted
        val filterIntervalCustom = prefs.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.libraryAnime.anime.isLocal() ||
                    it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryAnime.anime) > 0
            }
        }

        val filterFnUnseen: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterUnseen) { it.libraryAnime.unseenCount > 0 }
        }

        val filterFnStarted: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryAnime.hasStarted }
        }

        val filterFnBookmarked: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryAnime.hasBookmarks }
        }

        val filterFnCompleted: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryAnime.anime.status.toInt() == SAnime.COMPLETED }
        }

        val filterFnIntervalCustom: (AnimeLibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryAnime.anime.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (AnimeLibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val animeTracks = trackMap
                .mapValues { entry -> entry.value.map { it.trackerId } }[item.libraryAnime.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && animeTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || animeTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFn: (AnimeLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnseen(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it)
        }

        return mapValues { (_, value) -> value.fastFilter(filterFn) }
    }

    private fun AnimeLibraryMap.applySort(
        trackMap: Map<Long, List<AnimeTrack>>,
        loggedInTrackerIds: Set<Long>,
    ): AnimeLibraryMap {
        val sortAlphabetically: (AnimeLibraryItem, AnimeLibraryItem) -> Int = { i1, i2 ->
            i1.libraryAnime.anime.title.lowercase().compareToWithCollator(i2.libraryAnime.anime.title.lowercase())
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.animeService?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun AnimeLibrarySort.comparator(): Comparator<AnimeLibraryItem> = Comparator { i1, i2 ->
            when (this.type) {
                AnimeLibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                AnimeLibrarySort.Type.LastSeen -> {
                    i1.libraryAnime.lastSeen.compareTo(i2.libraryAnime.lastSeen)
                }
                AnimeLibrarySort.Type.LastUpdate -> {
                    i1.libraryAnime.anime.lastUpdate.compareTo(i2.libraryAnime.anime.lastUpdate)
                }
                AnimeLibrarySort.Type.UnseenCount -> when {
                    // Ensure unseen content comes first
                    i1.libraryAnime.unseenCount == i2.libraryAnime.unseenCount -> 0
                    i1.libraryAnime.unseenCount == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryAnime.unseenCount == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.libraryAnime.unseenCount.compareTo(i2.libraryAnime.unseenCount)
                }
                AnimeLibrarySort.Type.TotalEpisodes -> {
                    i1.libraryAnime.totalCount.compareTo(i2.libraryAnime.totalCount)
                }
                AnimeLibrarySort.Type.LatestEpisode -> {
                    i1.libraryAnime.latestUpload.compareTo(i2.libraryAnime.latestUpload)
                }
                AnimeLibrarySort.Type.EpisodeFetchDate -> {
                    i1.libraryAnime.episodeFetchedAt.compareTo(i2.libraryAnime.episodeFetchedAt)
                }
                AnimeLibrarySort.Type.DateAdded -> {
                    i1.libraryAnime.anime.dateAdded.compareTo(i2.libraryAnime.anime.dateAdded)
                }
                AnimeLibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[i1.libraryAnime.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[i2.libraryAnime.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                AnimeLibrarySort.Type.TimesWatched -> {
                    i1.libraryAnime.timesWatched.compareTo(i2.libraryAnime.timesWatched)
                }
                AnimeLibrarySort.Type.AiringTime -> when {
                    i1.libraryAnime.unseenCount != i2.libraryAnime.unseenCount ->
                        i1.libraryAnime.unseenCount.compareTo(i2.libraryAnime.unseenCount)
                    i1.libraryAnime.anime.nextEpisodeAiringAt == i2.libraryAnime.anime.nextEpisodeAiringAt -> 0
                    i1.libraryAnime.anime.nextEpisodeAiringAt == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryAnime.anime.nextEpisodeAiringAt == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.libraryAnime.anime.nextEpisodeAiringAt.compareTo(
                        i2.libraryAnime.anime.nextEpisodeAiringAt,
                    )
                }
                AnimeLibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
            }
        }

        val pinnedIds = libraryPreferences.pinnedAnimeIds().get()
        val pinnedFirst = compareByDescending<AnimeLibraryItem> { it.libraryAnime.id.toString() in pinnedIds }

        return mapValues { (key, value) ->
            // Reset transient flags (items are reused across re-emissions).
            value.forEach {
                it.isSeriesHead = false
                it.seriesMemberCount = 0
                it.seriesExpanded = false
                it.seriesCoverPath = null
                it.isFolder = false
            }

            if (key.sort.type == AnimeLibrarySort.Type.Random) {
                val shuffled = value.shuffled(Random(libraryPreferences.randomAnimeSortSeed().get()))
                return@mapValues if (pinnedIds.isEmpty()) shuffled else shuffled.sortedWith(pinnedFirst)
            }

            val comparator = pinnedFirst.then(
                key.sort.comparator()
                    .let { if (key.sort.isAscending) it else it.reversed() }
                    .thenComparator(sortAlphabetically),
            )

            value.sortedWith(comparator)
        }
    }

    /**
     * Replaces every custom-series member in each category with a single synthetic folder cell placed
     * at the position of the series' first (already-sorted) member. Non-series entries pass through
     * untouched. [folderMembers] provides the global member list backing each folder's cover collage.
     */
    private fun AnimeLibraryMap.foldIntoFolders(
        folderMembers: Map<String, List<AnimeLibraryItem>>,
    ): AnimeLibraryMap = mapValues { (_, items) ->
        val seenFolders = HashSet<String>()
        val result = ArrayList<AnimeLibraryItem>(items.size)
        for (item in items) {
            val name = item.seriesName
            val members = name?.let { folderMembers[it] }.orEmpty()
            when {
                // Not grouped, or a lone remnant (<2) that isn't a real folder: show as a normal entry.
                name == null || members.size < 2 -> result += item
                // First member of a real folder: replace the whole group with one folder cell.
                seenFolders.add(name) -> result += createFolderCell(name, members)
                // Subsequent members of an already-folded group are hidden.
            }
        }
        result
    }

    private fun createFolderCell(name: String, members: List<AnimeLibraryItem>): AnimeLibraryItem {
        return AnimeLibraryItem(libraryAnime = members.first().libraryAnime).apply {
            isFolder = true
            seriesName = name
            seriesMemberCount = members.size
            seriesCoverPath = seriesCoverCache.getCoverFile(isAnime = true, name = name)?.absolutePath
            folderPreviewCovers = members.take(4).map {
                val anime = it.libraryAnime.anime
                AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    url = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
                )
            }
        }
    }

    private fun getAnimelibItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.unreadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),
            libraryPreferences.autoUpdateItemRestrictions().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloadedAnime().changes(),
            libraryPreferences.filterUnseen().changes(),
            libraryPreferences.filterStartedAnime().changes(),
            libraryPreferences.filterBookmarkedAnime().changes(),
            libraryPreferences.filterCompletedAnime().changes(),
            libraryPreferences.filterIntervalCustom().changes(),
            transform = {
                ItemPreferences(
                    downloadBadge = it[0] as Boolean,
                    unseenBadge = it[1] as Boolean,
                    localBadge = it[2] as Boolean,
                    languageBadge = it[3] as Boolean,
                    skipOutsideReleasePeriod = LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                    globalFilterDownloaded = it[5] as Boolean,
                    filterDownloaded = it[6] as TriState,
                    filterUnseen = it[7] as TriState,
                    filterStarted = it[8] as TriState,
                    filterBookmarked = it[9] as TriState,
                    filterCompleted = it[10] as TriState,
                    filterIntervalCustom = it[11] as TriState,
                )
            },
        )
    }

    /**
     * Get the categories and all its anime from the database.
     */
    private fun getLibraryFlow(): Flow<AnimeLibraryMap> {
        val animelibAnimesFlow = combine(
            getLibraryAnime.subscribe(),
            getAnimelibItemPreferencesFlow(),
            downloadCache.changes,
            libraryPreferences.pinnedAnimeIds().changes(),
            libraryPreferences.seriesGroupingsAnime().changes(),
        ) { animelibAnimeList, prefs, _, pinnedIds, seriesSet ->
            val seriesById = SeriesGrouping.decode(seriesSet)
            animelibAnimeList
                .map { animelibAnime ->
                    // Display mode based on user preference: take it from global library setting or category
                    AnimeLibraryItem(
                        animelibAnime,
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(animelibAnime.anime).toLong()
                        } else {
                            0
                        },
                        unseenCount = if (prefs.unseenBadge) animelibAnime.unseenCount else 0,
                        isLocal = if (prefs.localBadge) animelibAnime.anime.isLocal() else false,
                        sourceLanguage = if (prefs.languageBadge) {
                            sourceManager.getOrStub(animelibAnime.anime.source).lang
                        } else {
                            ""
                        },
                        isPinned = animelibAnime.id.toString() in pinnedIds,
                        seriesName = seriesById[animelibAnime.id],
                    )
                }
        }

        return combine(
            getCategories.subscribe(),
            animelibAnimesFlow,
            libraryPreferences.libraryGroupModeAnime().changes(),
        ) { categories, libraryItems, groupModeValue ->
            when (LibraryGroupMode.fromInt(groupModeValue)) {
                LibraryGroupMode.BY_SOURCE -> groupBySource(libraryItems)
                LibraryGroupMode.BY_STATUS -> groupByStatus(libraryItems)
                LibraryGroupMode.BY_LANGUAGE -> groupByLanguage(libraryItems)
                LibraryGroupMode.BY_GENRE -> groupByGenre(libraryItems)
                LibraryGroupMode.NONE -> {
                    val byCategory = libraryItems.groupBy { it.libraryAnime.category }
                    val displayCategories = if (byCategory.isNotEmpty() && !byCategory.containsKey(0)) {
                        categories.fastFilterNot { it.isSystemCategory }
                    } else {
                        categories
                    }
                    displayCategories.associateWith { byCategory[it.id].orEmpty() }
                }
            }
        }
    }

    /** Groups the whole library into one synthetic category per source, sorted by source name. */
    private fun groupBySource(items: List<AnimeLibraryItem>): AnimeLibraryMap {
        return items.groupBy { it.libraryAnime.anime.source }
            .entries
            .sortedBy { sourceManager.getOrStub(it.key).name.lowercase() }
            .associate { (sourceId, entries) ->
                syntheticCategory(
                    id = -(sourceId + 1),
                    name = sourceManager.getOrStub(sourceId).name.ifBlank {
                        context.stringResource(MR.strings.unknown)
                    },
                ) to entries
            }
    }

    /** Groups the whole library into one synthetic category per publication status. */
    private fun groupByStatus(items: List<AnimeLibraryItem>): AnimeLibraryMap {
        return items.groupBy { it.libraryAnime.anime.status }
            .entries
            .sortedBy { it.key }
            .associate { (status, entries) ->
                syntheticCategory(id = -(2000L + status), name = statusLabel(status)) to entries
            }
    }

    /** Groups the whole library into one synthetic category per source language. */
    private fun groupByLanguage(items: List<AnimeLibraryItem>): AnimeLibraryMap {
        return items.groupBy { sourceManager.getOrStub(it.libraryAnime.anime.source).lang }
            .entries
            .sortedBy { LocaleHelper.getSourceDisplayName(it.key, context).lowercase() }
            .mapIndexed { index, (lang, entries) ->
                syntheticCategory(id = -(3000L + index), name = LocaleHelper.getSourceDisplayName(lang, context)) to
                    entries
            }
            .toMap()
    }

    /** Groups by genre/tag. An entry with several genres shows under each; untagged go to their own. */
    private fun groupByGenre(items: List<AnimeLibraryItem>): AnimeLibraryMap {
        val noGenreLabel = context.stringResource(MR.strings.unknown)
        val byGenre = LinkedHashMap<String, MutableList<AnimeLibraryItem>>()
        for (item in items) {
            val genres = item.libraryAnime.anime.genre
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                ?.distinct()
                .orEmpty()
            if (genres.isEmpty()) {
                byGenre.getOrPut(noGenreLabel) { mutableListOf() }.add(item)
            } else {
                genres.forEach { byGenre.getOrPut(it) { mutableListOf() }.add(item) }
            }
        }
        // Alphabetical, with the "untagged" bucket last.
        return byGenre.entries
            .sortedWith(compareBy({ it.key == noGenreLabel }, { it.key.lowercase() }))
            .mapIndexed { index, (genre, entries) ->
                syntheticCategory(id = -(5000L + index), name = genre) to entries.toList()
            }
            .toMap()
    }

    private fun syntheticCategory(id: Long, name: String) =
        Category(id = id, name = name, order = 0, flags = 0, hidden = false)

    private fun statusLabel(status: Long): String = context.stringResource(
        when (status) {
            SAnime.ONGOING.toLong() -> MR.strings.ongoing
            SAnime.COMPLETED.toLong() -> MR.strings.completed
            SAnime.LICENSED.toLong() -> MR.strings.licensed
            SAnime.PUBLISHING_FINISHED.toLong() -> MR.strings.publishing_finished
            SAnime.CANCELLED.toLong() -> MR.strings.cancelled
            SAnime.ON_HIATUS.toLong() -> MR.strings.on_hiatus
            else -> MR.strings.unknown
        },
    )

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFilterFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            val prefFlows = loggedInTrackers.map { tracker ->
                libraryPreferences.filterTrackedAnime(tracker.id.toInt()).changes()
            }
            combine(prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        }
    }

    /**
     * Returns the common categories for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getCommonCategories(animes: List<Anime>): Collection<Category> {
        if (animes.isEmpty()) return emptyList()
        return animes
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnseenEpisode(anime: Anime): Episode? {
        return getEpisodesByAnimeId.await(anime.id).getNextUnseen(anime, downloadManager)
    }

    /**
     * Returns the mix (non-common) categories for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getMixCategories(animes: List<Anime>): Collection<Category> {
        if (animes.isEmpty()) return emptyList()
        val nimeCategories = animes.map { getCategories.await(it.id).toSet() }
        val common = nimeCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return nimeCategories.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val animes = selection.map { it.anime }.toList()
        when (action) {
            DownloadAction.NEXT_1_ITEM -> downloadUnseenEpisodes(animes, 1)
            DownloadAction.NEXT_5_ITEMS -> downloadUnseenEpisodes(animes, 5)
            DownloadAction.NEXT_10_ITEMS -> downloadUnseenEpisodes(animes, 10)
            DownloadAction.NEXT_25_ITEMS -> downloadUnseenEpisodes(animes, 25)
            DownloadAction.UNVIEWED_ITEMS -> downloadUnseenEpisodes(animes, null)
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unseen episodes from the list of animes given.
     *
     * @param animes the list of anime.
     * @param amount the amount to queue or null to queue all
     */
    private fun downloadUnseenEpisodes(animes: List<Anime>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                val episodes = getNextEpisodes.await(anime.id)
                    .fastFilterNot { episode ->
                        downloadManager.getQueuedDownloadOrNull(episode.id) != null ||
                            downloadManager.isEpisodeDownloaded(
                                episode.name,
                                episode.scanlator,
                                anime.title,
                                anime.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadEpisodes(anime, episodes)
            }
        }
    }

    /**
     * Marks animes' episodes seen status.
     */
    fun markSeenSelection(seen: Boolean) {
        val animes = state.value.selection.toList()
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                setSeenStatus.await(
                    anime = anime.anime,
                    seen = seen,
                )
            }
        }
        clearSelection()
    }

    /**
     * Pins the selection to the top of the library, or unpins it if every selected entry is
     * already pinned.
     */
    fun togglePinSelection() {
        val pref = libraryPreferences.pinnedAnimeIds()
        val selectedIds = state.value.selection.map { it.id.toString() }.toSet()
        if (selectedIds.isEmpty()) return
        val current = pref.get()
        val allPinned = selectedIds.all { it in current }
        pref.set(if (allPinned) current - selectedIds else current + selectedIds)
        clearSelection()
    }

    /**
     * Opens the dialog to group the current selection into a named custom series.
     */
    fun openGroupIntoSeriesDialog() {
        val ids = state.value.selection.map { it.id }
        if (ids.isEmpty()) return
        val existing = SeriesGrouping.seriesNames(libraryPreferences.seriesGroupingsAnime().get())
        mutableState.update { it.copy(dialog = Dialog.GroupIntoSeries(ids, existing.toImmutableList())) }
    }

    /**
     * Assigns [ids] to the custom series [name] (creating it or adding to it).
     */
    fun groupIntoSeries(name: String, ids: List<Long>) {
        if (name.isBlank() || ids.isEmpty()) return
        val pref = libraryPreferences.seriesGroupingsAnime()
        pref.set(SeriesGrouping.assign(pref.get(), ids, name.trim()))
        clearSelection()
    }

    /**
     * Toggles the expanded/collapsed state of the custom series [name].
     */
    fun toggleSeriesExpanded(name: String?) {
        name ?: return
        mutableState.update {
            it.copy(
                seriesExpanded = if (name in it.seriesExpanded) {
                    it.seriesExpanded - name
                } else {
                    it.seriesExpanded + name
                },
            )
        }
    }

    /** Drills into the folder [name], showing only its members. */
    fun openFolder(name: String?) {
        name ?: return
        mutableState.update { it.copy(openFolder = name) }
    }

    /** Leaves the currently-open folder, back to the top level. */
    fun closeFolder() {
        mutableState.update { it.copy(openFolder = null) }
    }

    /** Whether the folder [name] has a custom cover set. */
    fun folderHasCover(name: String?): Boolean =
        name != null && seriesCoverCache.hasCover(isAnime = true, name = name)

    /** Opens the actions menu for the folder [name] (change cover, rename, disband). */
    fun showFolderActionsDialog(name: String?) {
        name ?: return
        val hasCover = seriesCoverCache.hasCover(isAnime = true, name = name)
        mutableState.update { it.copy(dialog = Dialog.FolderActions(name, hasCover)) }
    }

    /** Opens the rename dialog for the folder [name]. */
    fun showRenameFolderDialog(name: String) {
        mutableState.update { it.copy(dialog = Dialog.RenameFolder(name)) }
    }

    /** Renames the folder [oldName] to [newName], reassigning every member and moving its cover. */
    fun renameFolder(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
        val pref = libraryPreferences.seriesGroupingsAnime()
        val ids = SeriesGrouping.decode(pref.get()).filterValues { it == oldName }.keys.toList()
        if (ids.isEmpty()) return
        pref.set(SeriesGrouping.assign(pref.get(), ids, trimmed))
        seriesCoverCache.renameSeries(isAnime = true, oldName = oldName, newName = trimmed)
        mutableState.update { if (it.openFolder == oldName) it.copy(openFolder = trimmed) else it }
    }

    /** Disbands the folder [name], ungrouping all its members and dropping its cover. */
    fun disbandFolder(name: String) {
        val pref = libraryPreferences.seriesGroupingsAnime()
        val ids = SeriesGrouping.decode(pref.get()).filterValues { it == name }.keys.toList()
        if (ids.isNotEmpty()) pref.set(SeriesGrouping.remove(pref.get(), ids))
        seriesCoverCache.deleteCover(isAnime = true, name = name)
        mutableState.update { if (it.openFolder == name) it.copy(openFolder = null) else it }
    }

    /**
     * Removes the current selection from any custom series.
     */
    fun ungroupSelection() {
        val ids = state.value.selection.map { it.id }
        if (ids.isEmpty()) return
        val pref = libraryPreferences.seriesGroupingsAnime()
        val decoded = SeriesGrouping.decode(pref.get())
        // A collapsed series head only selects itself (its members are hidden), yet ungrouping should
        // disband the WHOLE group. So remove every entry that shares a series with any selection.
        val seriesOfSelection = ids.mapNotNull { decoded[it] }.toSet()
        val idsToRemove = decoded.filterValues { it in seriesOfSelection }.keys + ids
        pref.set(SeriesGrouping.remove(pref.get(), idsToRemove.toList()))
        clearSelection()
    }

    /**
     * The custom-series name shared by the whole current selection, or null if the selection is empty
     * or spans more than one series. Used to gate the "set group cover" action.
     */
    fun selectedSingleSeriesName(): String? {
        val ids = state.value.selection.map { it.id }
        if (ids.isEmpty()) return null
        val decoded = SeriesGrouping.decode(libraryPreferences.seriesGroupingsAnime().get())
        // singleOrNull() yields the shared name only when every id maps to the same non-null series.
        return ids.map { decoded[it] }.toSet().singleOrNull()
    }

    /** Whether the current selection's series already has a custom cover. */
    fun selectionHasSeriesCover(): Boolean {
        val name = selectedSingleSeriesName() ?: return false
        return seriesCoverCache.hasCover(isAnime = true, name = name)
    }

    /** Sets [uri] as the custom cover of the custom series [name]. */
    fun setSeriesCover(name: String, uri: Uri, context: Context) {
        screenModelScope.launchIO {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    seriesCoverCache.setCover(isAnime = true, name = name, inputStream = input)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                withUIContext { context.toast(MR.strings.notification_cover_update_failed) }
            }
        }
        clearSelection()
    }

    /** Removes the custom cover of the custom series [name]. */
    fun removeSeriesCover(name: String) {
        seriesCoverCache.deleteCover(isAnime = true, name = name)
        clearSelection()
    }

    /**
     * Remove the selected anime.
     *
     * @param animeList the list of anime to delete.
     * @param deleteFromLibrary whether to delete anime from library.
     * @param deleteEpisodes whether to delete downloaded episodes.
     */
    fun removeAnimes(animeList: List<Anime>, deleteFromLibrary: Boolean, deleteEpisodes: Boolean) {
        screenModelScope.launchNonCancellable {
            val animeToDelete = animeList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = animeToDelete.map {
                    it.removeCovers(coverCache)
                    it.removeBackgrounds(backgroundCache)
                    AnimeUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateAnime.awaitAll(toDelete)
            }

            if (deleteEpisodes) {
                animeToDelete.forEach { anime ->
                    val source = sourceManager.get(anime.source) as? AnimeHttpSource
                    if (source != null) {
                        downloadManager.deleteAnime(anime, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of anime using old and new common categories.
     *
     * @param animeList the list of anime to move.
     * @param addCategories the categories to add for all animes.
     * @param removeCategories the categories to remove in all animes.
     */
    fun setAnimeCategories(
        animeList: List<Anime>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            animeList.forEach { anime ->
                val categoryIds = getCategories.await(anime.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setAnimeCategories.await(anime.id, categoryIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (
            if (isLandscape) {
                libraryPreferences.animeLandscapeColumns()
            } else {
                libraryPreferences.animePortraitColumns()
            }
            ).asState(
            screenModelScope,
        )
    }

    suspend fun getRandomAnimelibItemForCurrentCategory(): AnimeLibraryItem? {
        if (state.value.categories.isEmpty()) return null

        return withIOContext {
            state.value
                .getAnimelibItemsByCategoryId(state.value.categories[activeCategoryIndex].id)
                ?.randomOrNull()
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun toggleSelection(anime: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (list.fastAny { it.id == anime.id }) {
                    list.removeAll { it.id == anime.id }
                } else {
                    list.add(anime)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all nimes between and including the given anime and the last pressed anime from the
     * same category as the given anime
     */
    fun toggleRangeSelection(anime: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.category != anime.category) {
                    list.add(anime)
                    return@mutate
                }

                val items = state.getAnimelibItemsByCategoryId(anime.category)
                    ?.fastMap { it.libraryAnime }.orEmpty()
                val lastAnimeIndex = items.indexOf(lastSelected)
                val curAnimeIndex = items.indexOf(anime)

                val selectedIds = list.fastMap { it.id }
                val selectionRange = when {
                    lastAnimeIndex < curAnimeIndex -> IntRange(lastAnimeIndex, curAnimeIndex)
                    curAnimeIndex < lastAnimeIndex -> IntRange(curAnimeIndex, lastAnimeIndex)
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                val newSelections = selectionRange.mapNotNull { index ->
                    items[index].takeUnless { it.id in selectedIds }
                }
                list.addAll(newSelections)
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories.getOrNull(index)?.id ?: -1
                val selectedIds = list.fastMap { it.id }
                state.getAnimelibItemsByCategoryId(categoryId)
                    ?.fastMapNotNull { item ->
                        item.libraryAnime.takeUnless { it.id in selectedIds }
                    }
                    ?.let { list.addAll(it) }
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories[index].id
                val items = state.getAnimelibItemsByCategoryId(categoryId)?.fastMap { it.libraryAnime }.orEmpty()
                val selectedIds = list.fastMap { it.id }
                val (toRemove, toAdd) = items.fastPartition { it.id in selectedIds }
                val toRemoveIds = toRemove.fastMap { it.id }
                list.removeAll { it.id in toRemoveIds }
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            // Create a copy of selected anime
            val animeList = state.value.selection.map { it.anime }

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.categories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(animeList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(animeList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(animeList, preselected)) }
        }
    }

    fun openDeleteAnimeDialog() {
        val nimeList = state.value.selection.map { it.anime }
        mutableState.update { it.copy(dialog = Dialog.DeleteAnime(nimeList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val anime: List<Anime>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteAnime(val anime: List<Anime>) : Dialog
        data class GroupIntoSeries(
            val ids: List<Long>,
            val existingNames: ImmutableList<String>,
        ) : Dialog
        data class FolderActions(val name: String, val hasCover: Boolean) : Dialog
        data class RenameFolder(val name: String) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unseenBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnseen: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: AnimeLibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryAnime> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showAnimeCount: Boolean = false,
        val showAnimeContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        val pinnedIds: Set<String> = emptySet(),
        val seriesExpanded: Set<String> = emptySet(),
        val seriesIds: Set<Long> = emptySet(),
        // Custom-series members keyed by series name, used by the drill-in folder view.
        val folderMembers: Map<String, List<AnimeLibraryItem>> = emptyMap(),
        // Name of the folder currently opened (drill-in), or null at the top level.
        val openFolder: String? = null,
    ) {
        private val libraryCount by lazy {
            library.values
                .flatten()
                .fastDistinctBy { it.libraryAnime.anime.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty()

        val categories = library.keys.toList()

        /** Members of the currently-open folder, or empty if none is open. */
        fun openFolderItems(): List<AnimeLibraryItem> = openFolder?.let { folderMembers[it] }.orEmpty()

        fun getAnimelibItemsByCategoryId(categoryId: Long): List<AnimeLibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == categoryId } }
        }

        fun getAnimelibItemsByPage(page: Int): List<AnimeLibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getAnimeCountForCategory(category: Category): Int? {
            return if (showAnimeCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = categories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showAnimeCount -> null
                !showCategoryTabs -> getAnimeCountForCategory(category)
                // Whole library count
                else -> libraryCount
            }

            return LibraryToolbarTitle(title, count)
        }
    }
}
