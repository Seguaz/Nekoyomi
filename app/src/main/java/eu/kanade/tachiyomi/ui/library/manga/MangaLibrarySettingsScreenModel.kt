package eu.kanade.tachiyomi.ui.library.manga

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.category.manga.interactor.SetSortModeForMangaCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MangaLibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setMangaDisplayMode: SetMangaDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForMangaCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
    // When true this backs the Novel library, so category-tab visibility uses the novel-only pref.
    val novelOnly: Boolean = false,
) : ScreenModel {

    /** Category-tabs toggle for the active library (novels keep their own, independent of manga). */
    fun categoryTabsPref() = if (novelOnly) {
        libraryPreferences.categoryTabsNovel()
    } else {
        libraryPreferences.categoryTabs()
    }

    // Preferences that are scoped per media type so tweaking the novel library doesn't change the
    // manga library (and vice versa). Each returns the novel-only pref when [novelOnly], else manga.
    fun groupModePref() = if (novelOnly) {
        libraryPreferences.libraryGroupModeNovel()
    } else {
        libraryPreferences.libraryGroupModeManga()
    }

    fun portraitColumnsPref() = if (novelOnly) {
        libraryPreferences.novelPortraitColumns()
    } else {
        libraryPreferences.mangaPortraitColumns()
    }

    fun landscapeColumnsPref() = if (novelOnly) {
        libraryPreferences.novelLandscapeColumns()
    } else {
        libraryPreferences.mangaLandscapeColumns()
    }

    fun filterDownloadedPref() = if (novelOnly) {
        libraryPreferences.filterDownloadedNovel()
    } else {
        libraryPreferences.filterDownloadedManga()
    }

    fun filterUnreadPref() = if (novelOnly) {
        libraryPreferences.filterUnreadNovel()
    } else {
        libraryPreferences.filterUnread()
    }

    fun filterStartedPref() = if (novelOnly) {
        libraryPreferences.filterStartedNovel()
    } else {
        libraryPreferences.filterStartedManga()
    }

    fun filterBookmarkedPref() = if (novelOnly) {
        libraryPreferences.filterBookmarkedNovel()
    } else {
        libraryPreferences.filterBookmarkedManga()
    }

    fun filterCompletedPref() = if (novelOnly) {
        libraryPreferences.filterCompletedNovel()
    } else {
        libraryPreferences.filterCompletedManga()
    }

    fun filterIntervalCustomPref() = if (novelOnly) {
        libraryPreferences.filterIntervalCustomNovel()
    } else {
        libraryPreferences.filterIntervalCustom()
    }

    fun filterTrackedPref(id: Int) = if (novelOnly) {
        libraryPreferences.filterTrackedNovel(id)
    } else {
        libraryPreferences.filterTrackedManga(id)
    }

    fun filterGenresIncludePref() = if (novelOnly) {
        libraryPreferences.filterGenresIncludeNovel()
    } else {
        libraryPreferences.filterGenresIncludeManga()
    }

    fun filterGenresExcludePref() = if (novelOnly) {
        libraryPreferences.filterGenresExcludeNovel()
    } else {
        libraryPreferences.filterGenresExcludeManga()
    }

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    fun toggleFilter(preference: Preference<TriState>) {
        preference.getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter(filterTrackedPref(id))
    }

    /** Cycles a tag through neutral -> included -> excluded -> neutral for the library tag filter. */
    fun cycleGenreFilter(genre: String) {
        val include = filterGenresIncludePref()
        val exclude = filterGenresExcludePref()
        when (genre) {
            in include.get() -> {
                include.set(include.get() - genre)
                exclude.set(exclude.get() + genre)
            }
            in exclude.get() -> exclude.set(exclude.get() - genre)
            else -> include.set(include.get() + genre)
        }
    }

    fun clearGenreFilters() {
        filterGenresIncludePref().set(emptySet())
        filterGenresExcludePref().set(emptySet())
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setMangaDisplayMode.await(mode)
    }

    fun setSort(
        category: Category?,
        mode: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
