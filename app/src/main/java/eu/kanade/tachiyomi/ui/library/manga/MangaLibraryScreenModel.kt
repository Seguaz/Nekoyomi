package eu.kanade.tachiyomi.ui.library.manga

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
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.library.SeriesGrouping
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
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
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.manga.model.sort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

/**
 * Typealias for the library manga, using the category as keys, and list of manga as values.
 */
typealias MangaLibraryMap = Map<Category, List<MangaLibraryItem>>

class MangaLibraryScreenModel(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getCategories: GetVisibleMangaCategories = Injekt.get(),
    private val getTracksPerManga: GetTracksPerManga = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: MangaCoverCache = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    private val downloadCache: MangaDownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<MangaLibraryScreenModel.State>(State()) {

    var activeCategoryIndex: Int by libraryPreferences.lastUsedMangaCategory().asState(
        screenModelScope,
    )

    init {
        screenModelScope.launchIO {
            val searchAndExpand = combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                state.map { it.seriesExpanded }.distinctUntilChanged(),
            ) { searchQuery, expanded -> searchQuery to expanded }
            combine(
                searchAndExpand,
                getLibraryFlow(),
                getTracksPerManga.subscribe(),
                getTrackingFilterFlow(),
                downloadCache.changes,
            ) { (searchQuery, expanded), library, tracks, trackingFilter, _ ->
                library
                    .applyFilters(tracks, trackingFilter)
                    .applySort(tracks, trackingFilter.keys, expanded, collapseSeries = searchQuery == null)
                    .mapValues { (_, value) ->
                        if (searchQuery != null) {
                            value.filter { it.matches(searchQuery) }
                        } else {
                            value
                        }
                    }
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            library = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueViewingButton().changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showMangaCount, showMangaContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showMangaCount = showMangaCount,
                        showMangaContinueButton = showMangaContinueButton,
                    )
                }
            }
            .launchIn(screenModelScope)

        combine(
            getLibraryItemPreferencesFlow(),
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            (
                listOf(
                    prefs.filterDownloaded,
                    prefs.filterUnread,
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

        libraryPreferences.pinnedMangaIds().changes()
            .onEach { pinned -> mutableState.update { it.copy(pinnedIds = pinned) } }
            .launchIn(screenModelScope)

        libraryPreferences.seriesGroupingsManga().changes()
            .onEach { set -> mutableState.update { it.copy(seriesIds = SeriesGrouping.decode(set).keys) } }
            .launchIn(screenModelScope)
    }

    private suspend fun MangaLibraryMap.applyFilters(
        trackMap: Map<Long, List<MangaTrack>>,
        trackingFilter: Map<Long, TriState>,
    ): MangaLibraryMap {
        val prefs = getLibraryItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val skipOutsideReleasePeriod = prefs.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnread = prefs.filterUnread
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted
        val filterIntervalCustom = prefs.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.libraryManga.manga.isLocal() ||
                    it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryManga.manga) > 0
            }
        }

        val filterFnUnread: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryManga.unreadCount > 0 }
        }

        val filterFnStarted: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryManga.hasStarted }
        }

        val filterFnBookmarked: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryManga.hasBookmarks }
        }

        val filterFnCompleted: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryManga.manga.status.toInt() == SManga.COMPLETED }
        }

        val filterFnIntervalCustom: (MangaLibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryManga.manga.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (MangaLibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val mangaTracks = trackMap
                .mapValues { entry -> entry.value.map { it.trackerId } }[item.libraryManga.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFn: (MangaLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it)
        }

        return mapValues { (_, value) -> value.fastFilter(filterFn) }
    }

    private fun MangaLibraryMap.applySort(
        trackMap: Map<Long, List<MangaTrack>>,
        loggedInTrackerIds: Set<Long>,
        expandedSeries: Set<String>,
        collapseSeries: Boolean,
    ): MangaLibraryMap {
        val sortAlphabetically: (MangaLibraryItem, MangaLibraryItem) -> Int = { i1, i2 ->
            i1.libraryManga.manga.title.lowercase().compareToWithCollator(i2.libraryManga.manga.title.lowercase())
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.mangaService?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun MangaLibrarySort.comparator(): Comparator<MangaLibraryItem> = Comparator { i1, i2 ->
            when (this.type) {
                MangaLibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                MangaLibrarySort.Type.LastRead -> {
                    i1.libraryManga.lastRead.compareTo(i2.libraryManga.lastRead)
                }
                MangaLibrarySort.Type.LastUpdate -> {
                    i1.libraryManga.manga.lastUpdate.compareTo(i2.libraryManga.manga.lastUpdate)
                }
                MangaLibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    i1.libraryManga.unreadCount == i2.libraryManga.unreadCount -> 0
                    i1.libraryManga.unreadCount == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryManga.unreadCount == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.libraryManga.unreadCount.compareTo(i2.libraryManga.unreadCount)
                }
                MangaLibrarySort.Type.TotalChapters -> {
                    i1.libraryManga.totalChapters.compareTo(i2.libraryManga.totalChapters)
                }
                MangaLibrarySort.Type.LatestChapter -> {
                    i1.libraryManga.latestUpload.compareTo(i2.libraryManga.latestUpload)
                }
                MangaLibrarySort.Type.ChapterFetchDate -> {
                    i1.libraryManga.chapterFetchedAt.compareTo(i2.libraryManga.chapterFetchedAt)
                }
                MangaLibrarySort.Type.DateAdded -> {
                    i1.libraryManga.manga.dateAdded.compareTo(i2.libraryManga.manga.dateAdded)
                }
                MangaLibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[i1.libraryManga.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[i2.libraryManga.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                MangaLibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
            }
        }

        val pinnedIds = libraryPreferences.pinnedMangaIds().get()
        val pinnedFirst = compareByDescending<MangaLibraryItem> { it.libraryManga.id.toString() in pinnedIds }

        return mapValues { (key, value) ->
            // Reset transient series-collapse flags (items are reused across re-emissions).
            value.forEach {
                it.isSeriesHead = false
                it.seriesMemberCount = 0
                it.seriesExpanded = false
            }

            if (key.sort.type == MangaLibrarySort.Type.Random) {
                val shuffled = value.shuffled(Random(libraryPreferences.randomMangaSortSeed().get()))
                return@mapValues if (pinnedIds.isEmpty()) shuffled else shuffled.sortedWith(pinnedFirst)
            }

            val comparator = pinnedFirst.then(
                key.sort.comparator()
                    .let { if (key.sort.isAscending) it else it.reversed() }
                    .thenComparator(sortAlphabetically),
            )

            if (!collapseSeries) {
                return@mapValues value.sortedWith(comparator)
            }

            // Collapse custom series: keep only the head of each multi-member series unless expanded.
            val groups = value.filter { it.seriesName != null }
                .groupBy { it.seriesName!! }
                .filter { it.value.size > 1 }
            if (groups.isEmpty()) return@mapValues value.sortedWith(comparator)

            val memberIds = groups.values.flatten().mapTo(HashSet()) { it.libraryManga.id }
            val headIds = groups.values
                .mapNotNull { members -> members.minWithOrNull(comparator) }
                .mapTo(HashSet()) { it.libraryManga.id }

            value.forEach { item ->
                val head = item.libraryManga.id in headIds
                item.isSeriesHead = head
                if (head) {
                    item.seriesMemberCount = groups[item.seriesName]!!.size
                    item.seriesExpanded = item.seriesName in expandedSeries
                }
            }

            // Representatives: entries not in any series, plus each series' head.
            val reps = value
                .filter { it.libraryManga.id !in memberIds || it.libraryManga.id in headIds }
                .sortedWith(comparator)

            reps.flatMap { rep ->
                if (rep.isSeriesHead && rep.seriesExpanded) {
                    listOf(rep) + groups[rep.seriesName]!!
                        .sortedWith(comparator)
                        .filter { it.libraryManga.id != rep.libraryManga.id }
                } else {
                    listOf(rep)
                }
            }
        }
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.unreadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),
            libraryPreferences.autoUpdateItemRestrictions().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloadedManga().changes(),
            libraryPreferences.filterUnread().changes(),
            libraryPreferences.filterStartedManga().changes(),
            libraryPreferences.filterBookmarkedManga().changes(),
            libraryPreferences.filterCompletedManga().changes(),
            libraryPreferences.filterIntervalCustom().changes(),
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                unreadBadge = it[1] as Boolean,
                localBadge = it[2] as Boolean,
                languageBadge = it[3] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                globalFilterDownloaded = it[5] as Boolean,
                filterDownloaded = it[6] as TriState,
                filterUnread = it[7] as TriState,
                filterStarted = it[8] as TriState,
                filterBookmarked = it[9] as TriState,
                filterCompleted = it[10] as TriState,
                filterIntervalCustom = it[11] as TriState,
            )
        }
    }

    /**
     * Get the categories and all its manga from the database.
     */
    private fun getLibraryFlow(): Flow<MangaLibraryMap> {
        val libraryMangasFlow = combine(
            getLibraryManga.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
            libraryPreferences.pinnedMangaIds().changes(),
            libraryPreferences.seriesGroupingsManga().changes(),
        ) { libraryMangaList, prefs, _, pinnedIds, seriesSet ->
            val seriesById = SeriesGrouping.decode(seriesSet)
            libraryMangaList
                .map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    MangaLibraryItem(
                        libraryManga,
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(libraryManga.manga).toLong()
                        } else {
                            0
                        },
                        unreadCount = if (prefs.unreadBadge) libraryManga.unreadCount else 0,
                        isLocal = if (prefs.localBadge) libraryManga.manga.isLocal() else false,
                        sourceLanguage = if (prefs.languageBadge) {
                            sourceManager.getOrStub(libraryManga.manga.source).lang
                        } else {
                            ""
                        },
                        isPinned = libraryManga.id.toString() in pinnedIds,
                        seriesName = seriesById[libraryManga.id],
                    )
                }
                .groupBy { it.libraryManga.category }
        }

        return combine(getCategories.subscribe(), libraryMangasFlow) { categories, libraryManga ->
            val displayCategories = if (libraryManga.isNotEmpty() && !libraryManga.containsKey(0)) {
                categories.fastFilterNot { it.isSystemCategory }
            } else {
                categories
            }

            displayCategories.associateWith { libraryManga[it.id].orEmpty() }
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFilterFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            val prefFlows = loggedInTrackers.map { tracker ->
                libraryPreferences.filterTrackedManga(tracker.id.toInt()).changes()
            }
            combine(prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnreadChapter(manga: Manga): Chapter? {
        return getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true).getNextUnread(manga, downloadManager)
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val mangas = selection.map { it.manga }.toList()
        when (action) {
            DownloadAction.NEXT_1_ITEM -> downloadUnreadChapters(mangas, 1)
            DownloadAction.NEXT_5_ITEMS -> downloadUnreadChapters(mangas, 5)
            DownloadAction.NEXT_10_ITEMS -> downloadUnreadChapters(mangas, 10)
            DownloadAction.NEXT_25_ITEMS -> downloadUnreadChapters(mangas, 25)
            DownloadAction.UNVIEWED_ITEMS -> downloadUnreadChapters(mangas, null)
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unread chapters from the list of mangas given.
     *
     * @param mangas the list of manga.
     * @param amount the amount to queue or null to queue all
     */
    private fun downloadUnreadChapters(mangas: List<Manga>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                val chapters = getNextChapters.await(manga.id)
                    .fastFilterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                manga.title,
                                manga.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    /**
     * Pins the selection to the top of the library, or unpins it if every selected entry is
     * already pinned.
     */
    fun togglePinSelection() {
        val pref = libraryPreferences.pinnedMangaIds()
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
        val existing = SeriesGrouping.seriesNames(libraryPreferences.seriesGroupingsManga().get())
        mutableState.update { it.copy(dialog = Dialog.GroupIntoSeries(ids, existing.toImmutableList())) }
    }

    /**
     * Assigns [ids] to the custom series [name] (creating it or adding to it).
     */
    fun groupIntoSeries(name: String, ids: List<Long>) {
        if (name.isBlank() || ids.isEmpty()) return
        val pref = libraryPreferences.seriesGroupingsManga()
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

    /**
     * Removes the current selection from any custom series.
     */
    fun ungroupSelection() {
        val ids = state.value.selection.map { it.id }
        if (ids.isEmpty()) return
        val pref = libraryPreferences.seriesGroupingsManga()
        pref.set(SeriesGrouping.remove(pref.get(), ids))
        clearSelection()
    }

    /**
     * Marks mangas' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val mangas = state.value.selection.toList()
        screenModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                setReadStatus.await(
                    manga = manga.manga,
                    read = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangaList the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangaList: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable {
            val mangaToDelete = mangaList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = mangaToDelete.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteManga(manga, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(
        mangaList: List<Manga>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            mangaList.forEach { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setMangaCategories.await(manga.id, categoryIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (
            if (isLandscape) {
                libraryPreferences.mangaLandscapeColumns()
            } else {
                libraryPreferences.mangaPortraitColumns()
            }
            ).asState(
            screenModelScope,
        )
    }

    suspend fun getRandomLibraryItemForCurrentCategory(): MangaLibraryItem? {
        if (state.value.categories.isEmpty()) return null

        return withIOContext {
            state.value
                .getLibraryItemsByCategoryId(state.value.categories[activeCategoryIndex].id)
                ?.randomOrNull()
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun toggleSelection(manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (list.fastAny { it.id == manga.id }) {
                    list.removeAll { it.id == manga.id }
                } else {
                    list.add(manga)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.category != manga.category) {
                    list.add(manga)
                    return@mutate
                }

                val items = state.getLibraryItemsByCategoryId(manga.category)
                    ?.fastMap { it.libraryManga }.orEmpty()
                val lastMangaIndex = items.indexOf(lastSelected)
                val curMangaIndex = items.indexOf(manga)

                val selectedIds = list.fastMap { it.id }
                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> IntRange(lastMangaIndex, curMangaIndex)
                    curMangaIndex < lastMangaIndex -> IntRange(curMangaIndex, lastMangaIndex)
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
                state.getLibraryItemsByCategoryId(categoryId)
                    ?.fastMapNotNull { item ->
                        item.libraryManga.takeUnless { it.id in selectedIds }
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
                val items = state.getLibraryItemsByCategoryId(categoryId)?.fastMap { it.libraryManga }.orEmpty()
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
            // Create a copy of selected manga
            val mangaList = state.value.selection.map { it.manga }

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.categories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(mangaList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(mangaList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(mangaList, preselected)) }
        }
    }

    fun openDeleteMangaDialog() {
        val mangaList = state.value.selection.map { it.manga }
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(mangaList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val manga: List<Manga>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteManga(val manga: List<Manga>) : Dialog
        data class GroupIntoSeries(
            val ids: List<Long>,
            val existingNames: ImmutableList<String>,
        ) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: MangaLibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryManga> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        val pinnedIds: Set<String> = emptySet(),
        val seriesExpanded: Set<String> = emptySet(),
        val seriesIds: Set<Long> = emptySet(),
    ) {
        private val libraryCount by lazy {
            library.values
                .flatten()
                .fastDistinctBy { it.libraryManga.manga.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty()

        val categories = library.keys.toList()

        fun getLibraryItemsByCategoryId(categoryId: Long): List<MangaLibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == categoryId } }
        }

        fun getLibraryItemsByPage(page: Int): List<MangaLibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getMangaCountForCategory(category: Category): Int? {
            return if (showMangaCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
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
                !showMangaCount -> null
                !showCategoryTabs -> getMangaCountForCategory(category)
                // Whole library count
                else -> libraryCount
            }

            return LibraryToolbarTitle(title, count)
        }
    }
}
